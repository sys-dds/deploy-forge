package com.deployforge.api.promotion;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.deployforge.api.artifact.ReleaseArtifactRepository;
import com.deployforge.api.artifact.ReleaseArtifactResponse;
import com.deployforge.api.environment.EnvironmentRepository;
import com.deployforge.api.environment.EnvironmentResponse;
import com.deployforge.api.event.DeploymentIntentEventRepository;
import com.deployforge.api.event.DeploymentIntentEventType;
import com.deployforge.api.service.ServiceRepository;
import com.deployforge.api.service.ServiceResponse;
import com.deployforge.api.shared.ApiException;
import com.deployforge.api.shared.Jsonb;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PromotionEvidenceService {
    private final ServiceRepository serviceRepository;
    private final ReleaseArtifactRepository artifactRepository;
    private final EnvironmentRepository environmentRepository;
    private final PromotionEvidenceRepository evidenceRepository;
    private final DeploymentIntentEventRepository eventRepository;

    public PromotionEvidenceService(ServiceRepository serviceRepository, ReleaseArtifactRepository artifactRepository,
            EnvironmentRepository environmentRepository, PromotionEvidenceRepository evidenceRepository,
            DeploymentIntentEventRepository eventRepository) {
        this.serviceRepository = serviceRepository;
        this.artifactRepository = artifactRepository;
        this.environmentRepository = environmentRepository;
        this.evidenceRepository = evidenceRepository;
        this.eventRepository = eventRepository;
    }

    public PromotionEvidenceResponse create(UUID projectId, UUID serviceId, UUID artifactId, CreatePromotionEvidenceRequest request) {
        requireArtifact(projectId, serviceId, artifactId);
        EnvironmentResponse source = requireEnvironment(projectId, request.sourceEnvironmentId());
        if (request.targetEnvironmentId() != null) {
            EnvironmentResponse target = requireEnvironment(projectId, request.targetEnvironmentId());
            if (source.id().equals(target.id())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PROMOTION_EVIDENCE_INVALID", "Source and target environment cannot match");
            }
        }
        return evidenceRepository.findNatural(artifactId, request.sourceEnvironmentId(), request.targetEnvironmentId(),
                request.evidenceType(), request.evidenceRef()).map(existing -> idempotent(existing, request))
                .orElseGet(() -> {
                    PromotionEvidenceResponse created = evidenceRepository.create(projectId, serviceId, artifactId, request);
                    eventRepository.record(projectId, null, serviceId, request.targetEnvironmentId(), artifactId,
                            DeploymentIntentEventType.PROMOTION_EVIDENCE_RECORDED, request.recordedBy(), request.reason(),
                            Jsonb.object().put("evidenceType", request.evidenceType().name()));
                    return created;
                });
    }

    public List<PromotionEvidenceResponse> list(UUID projectId, UUID serviceId, UUID artifactId) {
        requireArtifact(projectId, serviceId, artifactId);
        return evidenceRepository.list(projectId, serviceId, artifactId);
    }

    private PromotionEvidenceResponse idempotent(PromotionEvidenceResponse existing, CreatePromotionEvidenceRequest request) {
        if (!Objects.equals(existing.recordedBy(), request.recordedBy())
                || !Objects.equals(existing.reason(), request.reason())
                || !Objects.equals(existing.metadata(), Jsonb.emptyObjectIfNull(request.metadata()))) {
            throw new ApiException(HttpStatus.CONFLICT, "PROMOTION_EVIDENCE_CONFLICT",
                    "Promotion evidence already exists with different immutable fields");
        }
        return existing;
    }

    private void requireArtifact(UUID projectId, UUID serviceId, UUID artifactId) {
        ServiceResponse service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "Service not found"));
        if (!service.projectId().equals(projectId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "Service not found");
        }
        ReleaseArtifactResponse artifact = artifactRepository.findById(artifactId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ARTIFACT_NOT_FOUND", "Artifact not found"));
        if (!artifact.projectId().equals(projectId) || !artifact.serviceId().equals(serviceId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ARTIFACT_NOT_FOUND", "Artifact not found");
        }
    }

    private EnvironmentResponse requireEnvironment(UUID projectId, UUID environmentId) {
        EnvironmentResponse environment = environmentRepository.findById(environmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ENVIRONMENT_NOT_FOUND", "Environment not found"));
        if (!environment.projectId().equals(projectId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ENVIRONMENT_NOT_FOUND", "Environment not found");
        }
        return environment;
    }
}
