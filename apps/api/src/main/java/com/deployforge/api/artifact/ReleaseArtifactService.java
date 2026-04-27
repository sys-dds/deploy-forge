package com.deployforge.api.artifact;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.deployforge.api.environment.EnvironmentRepository;
import com.deployforge.api.environment.EnvironmentResponse;
import com.deployforge.api.environment.EnvironmentType;
import com.deployforge.api.event.DeploymentIntentEventRepository;
import com.deployforge.api.event.DeploymentIntentEventType;
import com.deployforge.api.project.LifecycleStatus;
import com.deployforge.api.project.ProjectRepository;
import com.deployforge.api.project.ProjectResponse;
import com.deployforge.api.service.ServiceRepository;
import com.deployforge.api.service.ServiceResponse;
import com.deployforge.api.service.ServiceTier;
import com.deployforge.api.shared.ApiException;
import com.deployforge.api.shared.Jsonb;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ReleaseArtifactService {

    private final ProjectRepository projectRepository;
    private final ServiceRepository serviceRepository;
    private final EnvironmentRepository environmentRepository;
    private final ReleaseArtifactRepository artifactRepository;
    private final ArtifactEvidenceRepository evidenceRepository;
    private final DeploymentIntentEventRepository eventRepository;

    public ReleaseArtifactService(ProjectRepository projectRepository, ServiceRepository serviceRepository,
            EnvironmentRepository environmentRepository, ReleaseArtifactRepository artifactRepository,
            ArtifactEvidenceRepository evidenceRepository, DeploymentIntentEventRepository eventRepository) {
        this.projectRepository = projectRepository;
        this.serviceRepository = serviceRepository;
        this.environmentRepository = environmentRepository;
        this.artifactRepository = artifactRepository;
        this.evidenceRepository = evidenceRepository;
        this.eventRepository = eventRepository;
    }

    public ReleaseArtifactResponse register(UUID projectId, UUID serviceId, RegisterReleaseArtifactRequest request) {
        requireProject(projectId);
        requireServiceInProject(projectId, serviceId);
        return artifactRepository.findByServiceAndVersion(serviceId, request.version())
                .map(existing -> idempotentArtifact(existing, request))
                .orElseGet(() -> {
                    ReleaseArtifactResponse created = artifactRepository.create(projectId, serviceId, request);
                    eventRepository.record(projectId, null, serviceId, null, created.id(),
                            DeploymentIntentEventType.ARTIFACT_REGISTERED, request.createdBy(), "Artifact registered",
                            Jsonb.object().put("version", created.version()));
                    return created;
                });
    }

    public ReleaseArtifactResponse get(UUID projectId, UUID serviceId, UUID artifactId) {
        requireProject(projectId);
        requireServiceInProject(projectId, serviceId);
        ReleaseArtifactResponse artifact = artifactRepository.findById(artifactId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ARTIFACT_NOT_FOUND", "Artifact not found"));
        if (!artifact.projectId().equals(projectId) || !artifact.serviceId().equals(serviceId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ARTIFACT_NOT_FOUND", "Artifact not found");
        }
        return artifact;
    }

    public List<ReleaseArtifactResponse> list(UUID projectId, UUID serviceId) {
        requireProject(projectId);
        requireServiceInProject(projectId, serviceId);
        return artifactRepository.findByService(serviceId);
    }

    public ArtifactEvidenceResponse addEvidence(UUID projectId, UUID serviceId, UUID artifactId,
            AddArtifactEvidenceRequest request) {
        ReleaseArtifactResponse artifact = get(projectId, serviceId, artifactId);
        return evidenceRepository.findByNaturalKey(artifact.id(), request.evidenceType(), request.evidenceRef())
                .map(existing -> idempotentEvidence(existing, request, projectId, serviceId, artifactId))
                .orElseGet(() -> {
                    ArtifactEvidenceResponse created = evidenceRepository.create(artifact.id(), request);
                    eventRepository.record(projectId, null, serviceId, null, artifactId,
                            DeploymentIntentEventType.ARTIFACT_EVIDENCE_ADDED, "system", "Artifact evidence added",
                            Jsonb.object().put("evidenceType", request.evidenceType().name())
                                    .put("evidenceRef", request.evidenceRef()));
                    return created;
                });
    }

    public List<ArtifactEvidenceResponse> listEvidence(UUID projectId, UUID serviceId, UUID artifactId) {
        ReleaseArtifactResponse artifact = get(projectId, serviceId, artifactId);
        return evidenceRepository.findByArtifact(artifact.id());
    }

    public ArtifactDeployabilityResponse deployability(UUID projectId, UUID serviceId, UUID artifactId, UUID environmentId) {
        ReleaseArtifactResponse artifact = get(projectId, serviceId, artifactId);
        ServiceResponse service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "Service not found"));
        ProjectResponse project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "Project not found"));
        EnvironmentResponse environment = environmentRepository.findById(environmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ENVIRONMENT_NOT_FOUND", "Environment not found"));
        if (!environment.projectId().equals(projectId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ENVIRONMENT_NOT_FOUND", "Environment not found");
        }
        return evaluate(project, service, artifact, environment);
    }

    public ArtifactDeployabilityResponse evaluate(ProjectResponse project, ServiceResponse service,
            ReleaseArtifactResponse artifact, EnvironmentResponse environment) {
        java.util.ArrayList<DeployabilityCheckResponse> checks = new java.util.ArrayList<>();
        checks.add(check(artifact.readinessStatus() == ArtifactReadinessStatus.READY, "ARTIFACT_READY", "Artifact is ready"));
        checks.add(check(project.lifecycleStatus() == LifecycleStatus.ACTIVE, "PROJECT_ACTIVE", "Project is active"));
        checks.add(check(service.lifecycleStatus() == LifecycleStatus.ACTIVE, "SERVICE_ACTIVE", "Service is active"));
        checks.add(check(environment.lifecycleStatus() == LifecycleStatus.ACTIVE, "ENVIRONMENT_ACTIVE", "Environment is active"));
        boolean hasTest = evidenceRepository.existsByArtifactAndType(artifact.id(), EvidenceType.TEST_REPORT);
        boolean hasScan = evidenceRepository.existsByArtifactAndType(artifact.id(), EvidenceType.IMAGE_SCAN)
                || evidenceRepository.existsByArtifactAndType(artifact.id(), EvidenceType.SECURITY_SCAN);
        if (service.serviceTier() == ServiceTier.CRITICAL) {
            checks.add(check(hasTest, "CRITICAL_SERVICE_TEST_REPORT", "Critical service has test evidence"));
        }
        if (environment.protectedEnvironment() && environment.environmentType() == EnvironmentType.PROD) {
            checks.add(check(hasTest, "PROD_TEST_REPORT", "Protected production has test evidence"));
            checks.add(check(hasScan, "PROD_IMAGE_OR_SECURITY_SCAN", "Protected production has image or security scan evidence"));
        }
        boolean deployable = checks.stream().noneMatch(check -> check.status().equals("FAIL"));
        return new ArtifactDeployabilityResponse(artifact.id(), environment.id(), deployable, checks);
    }

    private ReleaseArtifactResponse idempotentArtifact(ReleaseArtifactResponse existing, RegisterReleaseArtifactRequest request) {
        boolean same = Objects.equals(existing.gitSha(), request.gitSha())
                && Objects.equals(existing.imageDigest(), request.imageDigest())
                && Objects.equals(existing.buildNumber(), request.buildNumber())
                && Objects.equals(existing.sourceBranch(), request.sourceBranch())
                && Objects.equals(existing.commitMessage(), request.commitMessage())
                && Objects.equals(existing.createdBy(), request.createdBy())
                && Objects.equals(existing.metadata(), Jsonb.emptyObjectIfNull(request.metadata()));
        if (!same) {
            throw new ApiException(HttpStatus.CONFLICT, "REQUEST_CONFLICT",
                    "Artifact version already exists with different immutable fields");
        }
        return existing;
    }

    private ArtifactEvidenceResponse idempotentEvidence(ArtifactEvidenceResponse existing, AddArtifactEvidenceRequest request,
            UUID projectId, UUID serviceId, UUID artifactId) {
        boolean same = Objects.equals(existing.evidenceSha(), request.evidenceSha())
                && Objects.equals(existing.metadata(), Jsonb.emptyObjectIfNull(request.metadata()));
        if (!same) {
            throw new ApiException(HttpStatus.CONFLICT, "REQUEST_CONFLICT",
                    "Evidence already exists with different immutable fields");
        }
        return existing;
    }

    private void requireProject(UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "Project not found");
        }
    }

    private ServiceResponse requireServiceInProject(UUID projectId, UUID serviceId) {
        ServiceResponse service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "Service not found"));
        if (!service.projectId().equals(projectId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "Service not found");
        }
        return service;
    }

    private DeployabilityCheckResponse check(boolean pass, String code, String message) {
        return new DeployabilityCheckResponse(code, pass ? "PASS" : "FAIL", message);
    }
}
