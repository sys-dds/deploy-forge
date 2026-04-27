package com.deployforge.api.policy;

import java.util.UUID;

import com.deployforge.api.environment.EnvironmentRepository;
import com.deployforge.api.environment.EnvironmentResponse;
import com.deployforge.api.event.DeploymentIntentEventRepository;
import com.deployforge.api.event.DeploymentIntentEventType;
import com.deployforge.api.project.ProjectRepository;
import com.deployforge.api.shared.ApiException;
import com.deployforge.api.shared.Jsonb;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ProtectedEnvironmentPolicyService {
    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final ProtectedEnvironmentPolicyRepository policyRepository;
    private final DeploymentIntentEventRepository eventRepository;

    public ProtectedEnvironmentPolicyService(ProjectRepository projectRepository, EnvironmentRepository environmentRepository,
            ProtectedEnvironmentPolicyRepository policyRepository, DeploymentIntentEventRepository eventRepository) {
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.policyRepository = policyRepository;
        this.eventRepository = eventRepository;
    }

    public ProtectedEnvironmentPolicyResponse upsert(UUID projectId, UUID environmentId, UpsertProtectedEnvironmentPolicyRequest request) {
        requireProject(projectId);
        requireEnvironment(projectId, environmentId);
        int count = request.requiredApprovalCount() == null ? 1 : request.requiredApprovalCount();
        if (Boolean.TRUE.equals(request.requireApproval()) && count <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROTECTED_POLICY_INVALID", "Required approval count must be positive when approval is required");
        }
        ProtectedEnvironmentPolicyResponse policy = policyRepository.upsert(projectId, environmentId, request);
        eventRepository.record(projectId, null, null, environmentId, null,
                DeploymentIntentEventType.PROTECTED_POLICY_UPSERTED, request.createdBy(), "Protected environment policy upserted", Jsonb.object());
        return policy;
    }

    public ProtectedEnvironmentPolicyResponse get(UUID projectId, UUID environmentId) {
        requireProject(projectId);
        requireEnvironment(projectId, environmentId);
        return policyRepository.find(projectId, environmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROTECTED_POLICY_NOT_FOUND", "Protected environment policy not found"));
    }

    private void requireProject(UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "Project not found");
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
