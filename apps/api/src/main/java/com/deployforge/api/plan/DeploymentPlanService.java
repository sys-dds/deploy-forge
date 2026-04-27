package com.deployforge.api.plan;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import com.deployforge.api.artifact.ArtifactDeployabilityResponse;
import com.deployforge.api.artifact.ArtifactEvidenceRepository;
import com.deployforge.api.artifact.ReleaseArtifactRepository;
import com.deployforge.api.artifact.ReleaseArtifactResponse;
import com.deployforge.api.artifact.ReleaseArtifactService;
import com.deployforge.api.environment.EnvironmentRepository;
import com.deployforge.api.environment.EnvironmentResponse;
import com.deployforge.api.event.DeploymentIntentEventRepository;
import com.deployforge.api.event.DeploymentIntentEventType;
import com.deployforge.api.project.LifecycleStatus;
import com.deployforge.api.project.ProjectRepository;
import com.deployforge.api.project.ProjectResponse;
import com.deployforge.api.service.ServiceRepository;
import com.deployforge.api.service.ServiceResponse;
import com.deployforge.api.shared.ApiException;
import com.deployforge.api.shared.Jsonb;
import com.deployforge.api.state.EnvironmentDeploymentStateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class DeploymentPlanService {

    private final ProjectRepository projectRepository;
    private final ServiceRepository serviceRepository;
    private final EnvironmentRepository environmentRepository;
    private final ReleaseArtifactRepository artifactRepository;
    private final ArtifactEvidenceRepository evidenceRepository;
    private final ReleaseArtifactService artifactService;
    private final DeploymentPlanRepository planRepository;
    private final EnvironmentDeploymentStateRepository stateRepository;
    private final DeploymentIntentEventRepository eventRepository;
    private final RiskCalculator riskCalculator;

    public DeploymentPlanService(ProjectRepository projectRepository, ServiceRepository serviceRepository,
            EnvironmentRepository environmentRepository, ReleaseArtifactRepository artifactRepository,
            ArtifactEvidenceRepository evidenceRepository, ReleaseArtifactService artifactService,
            DeploymentPlanRepository planRepository, EnvironmentDeploymentStateRepository stateRepository,
            DeploymentIntentEventRepository eventRepository, RiskCalculator riskCalculator) {
        this.projectRepository = projectRepository;
        this.serviceRepository = serviceRepository;
        this.environmentRepository = environmentRepository;
        this.artifactRepository = artifactRepository;
        this.evidenceRepository = evidenceRepository;
        this.artifactService = artifactService;
        this.planRepository = planRepository;
        this.stateRepository = stateRepository;
        this.eventRepository = eventRepository;
        this.riskCalculator = riskCalculator;
    }

    public DeploymentPlanResponse create(UUID projectId, String idempotencyKey, CreateDeploymentPlanRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Idempotency-Key header is required");
        }
        String requestHash = requestHash(request);
        return planRepository.findByProjectAndIdempotencyKey(projectId, idempotencyKey)
                .map(existing -> replayOrConflict(projectId, existing, requestHash, request))
                .orElseGet(() -> createNew(projectId, idempotencyKey, requestHash, request));
    }

    public List<DeploymentPlanResponse> list(UUID projectId) {
        requireProject(projectId);
        return planRepository.findByProject(projectId);
    }

    public DeploymentPlanResponse get(UUID projectId, UUID planId) {
        requireProject(projectId);
        return planRepository.findById(projectId, planId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "Deployment plan not found"));
    }

    public DeploymentPlanResponse cancel(UUID projectId, UUID planId, CancelDeploymentPlanRequest request) {
        DeploymentPlanResponse plan = get(projectId, planId);
        if (plan.status() == DeploymentPlanStatus.CANCELLED) {
            if (request.cancelledBy().equals(plan.cancelledBy()) && request.reason().equals(plan.cancelReason())) {
                return plan;
            }
            throw new ApiException(HttpStatus.CONFLICT, "REQUEST_CONFLICT", "Deployment plan is already cancelled");
        }
        DeploymentPlanResponse cancelled = planRepository.cancel(plan, request);
        eventRepository.record(projectId, plan.id(), plan.serviceId(), plan.targetEnvironmentId(), plan.artifactId(),
                DeploymentIntentEventType.PLAN_CANCELLED, request.cancelledBy(), request.reason(), Jsonb.object());
        return cancelled;
    }

    private DeploymentPlanResponse createNew(UUID projectId, String idempotencyKey, String requestHash,
            CreateDeploymentPlanRequest request) {
        ProjectResponse project = requireActiveProject(projectId);
        ServiceResponse service = serviceRepository.findById(request.serviceId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "Service not found"));
        if (!service.projectId().equals(projectId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "Service not found");
        }
        if (service.lifecycleStatus() != LifecycleStatus.ACTIVE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Service is not active");
        }
        EnvironmentResponse environment = environmentRepository.findById(request.targetEnvironmentId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ENVIRONMENT_NOT_FOUND", "Environment not found"));
        if (!environment.projectId().equals(projectId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ENVIRONMENT_NOT_FOUND", "Environment not found");
        }
        if (environment.lifecycleStatus() != LifecycleStatus.ACTIVE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Environment is not active");
        }
        ReleaseArtifactResponse artifact = artifactRepository.findById(request.artifactId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ARTIFACT_NOT_FOUND", "Artifact not found"));
        if (!artifact.projectId().equals(projectId) || !artifact.serviceId().equals(service.id())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ARTIFACT_NOT_FOUND", "Artifact not found");
        }
        ArtifactDeployabilityResponse deployability = artifactService.evaluate(project, service, artifact, environment);
        if (!deployability.deployable()) {
            eventRepository.record(projectId, null, service.id(), environment.id(), artifact.id(),
                    DeploymentIntentEventType.DEPLOYABILITY_CHECK_FAILED, request.requestedBy(),
                    "Artifact is not deployable", Jsonb.MAPPER.valueToTree(deployability));
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Artifact is not deployable");
        }
        RiskCalculation risk = riskCalculator.calculate(environment, service, request.strategy());
        JsonNode snapshot = snapshot(artifact, service, environment, deployability, risk);
        DeploymentPlanResponse plan = planRepository.create(projectId, request, idempotencyKey, requestHash, risk, snapshot);
        stateRepository.markPlanned(projectId, service.id(), environment.id(), plan.id());
        eventRepository.record(projectId, plan.id(), service.id(), environment.id(), artifact.id(),
                DeploymentIntentEventType.PLAN_CREATED, request.requestedBy(), request.reason(), Jsonb.object());
        eventRepository.record(projectId, plan.id(), service.id(), environment.id(), artifact.id(),
                DeploymentIntentEventType.ENVIRONMENT_STATE_PLANNED, request.requestedBy(), "Environment state planned",
                Jsonb.object());
        return plan;
    }

    private DeploymentPlanResponse replayOrConflict(UUID projectId, DeploymentPlanResponse existing, String requestHash,
            CreateDeploymentPlanRequest request) {
        if (!existing.requestHash().equals(requestHash)) {
            eventRepository.record(projectId, existing.id(), request.serviceId(), request.targetEnvironmentId(),
                    request.artifactId(), DeploymentIntentEventType.PLAN_IDEMPOTENCY_CONFLICT,
                    request.requestedBy(), "Idempotency key reused with different payload", Jsonb.object());
            throw new ApiException(HttpStatus.CONFLICT, "REQUEST_CONFLICT",
                    "Idempotency key already used with different request payload");
        }
        eventRepository.record(projectId, existing.id(), existing.serviceId(), existing.targetEnvironmentId(),
                existing.artifactId(), DeploymentIntentEventType.PLAN_IDEMPOTENT_REPLAYED,
                request.requestedBy(), "Idempotent deployment plan replayed", Jsonb.object());
        return existing;
    }

    private ProjectResponse requireProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "Project not found"));
    }

    private ProjectResponse requireActiveProject(UUID projectId) {
        ProjectResponse project = requireProject(projectId);
        if (project.lifecycleStatus() != LifecycleStatus.ACTIVE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Project is not active");
        }
        return project;
    }

    private String requestHash(CreateDeploymentPlanRequest request) {
        String value = request.serviceId() + "|" + request.artifactId() + "|" + request.targetEnvironmentId()
                + "|" + request.strategy() + "|" + request.reason() + "|" + request.requestedBy();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private JsonNode snapshot(ReleaseArtifactResponse artifact, ServiceResponse service, EnvironmentResponse environment,
            ArtifactDeployabilityResponse deployability, RiskCalculation risk) {
        ObjectNode root = Jsonb.object();
        root.putObject("artifact")
                .put("artifactId", artifact.id().toString())
                .put("version", artifact.version())
                .put("gitSha", artifact.gitSha())
                .put("imageDigest", artifact.imageDigest())
                .put("buildNumber", artifact.buildNumber());
        root.putObject("service")
                .put("serviceId", service.id().toString())
                .put("serviceTier", service.serviceTier().name())
                .put("runtimeKind", service.runtimeKind().name());
        root.putObject("environment")
                .put("environmentId", environment.id().toString())
                .put("environmentType", environment.environmentType().name())
                .put("protectedEnvironment", environment.protectedEnvironment())
                .put("requiresApproval", environment.requiresApproval());
        root.set("deployability", Jsonb.MAPPER.valueToTree(deployability));
        ObjectNode riskNode = root.putObject("risk");
        riskNode.put("riskLevel", risk.riskLevel().name());
        ArrayNode reasons = riskNode.putArray("riskReasons");
        risk.riskReasons().forEach(reasons::add);
        ArrayNode refs = root.putArray("evidenceRefs");
        evidenceRepository.findByArtifact(artifact.id()).forEach(evidence -> refs.addObject()
                .put("evidenceType", evidence.evidenceType().name())
                .put("evidenceRef", evidence.evidenceRef())
                .put("evidenceSha", evidence.evidenceSha()));
        root.put("capturedAt", OffsetDateTime.now().toString());
        return root;
    }
}
