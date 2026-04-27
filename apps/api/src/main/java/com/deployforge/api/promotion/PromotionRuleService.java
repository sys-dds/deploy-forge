package com.deployforge.api.promotion;

import java.util.List;
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
public class PromotionRuleService {
    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final PromotionRuleRepository ruleRepository;
    private final DeploymentIntentEventRepository eventRepository;

    public PromotionRuleService(ProjectRepository projectRepository, EnvironmentRepository environmentRepository,
            PromotionRuleRepository ruleRepository, DeploymentIntentEventRepository eventRepository) {
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.ruleRepository = ruleRepository;
        this.eventRepository = eventRepository;
    }

    public PromotionRuleResponse upsert(UUID projectId, UUID environmentId, UpsertPromotionRuleRequest request) {
        requireProject(projectId);
        EnvironmentResponse target = requireEnvironment(projectId, environmentId);
        if (request.requiredSourceEnvironmentId() != null) {
            EnvironmentResponse source = requireEnvironment(projectId, request.requiredSourceEnvironmentId());
            if (source.id().equals(target.id())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PROMOTION_RULE_INVALID", "Source environment cannot equal target environment");
            }
            if (source.sortOrder() >= target.sortOrder()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PROMOTION_RULE_INVALID", "Source environment must be earlier than target environment");
            }
        }
        PromotionRuleResponse rule = ruleRepository.upsert(projectId, environmentId, request);
        eventRepository.record(projectId, null, null, environmentId, null,
                DeploymentIntentEventType.PROMOTION_RULE_UPSERTED, request.createdBy(), "Promotion rule upserted", Jsonb.object());
        return rule;
    }

    public PromotionRuleResponse get(UUID projectId, UUID environmentId) {
        requireProject(projectId);
        requireEnvironment(projectId, environmentId);
        return ruleRepository.find(projectId, environmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROMOTION_RULE_NOT_FOUND", "Promotion rule not found"));
    }

    public List<PromotionRuleResponse> list(UUID projectId) {
        requireProject(projectId);
        return ruleRepository.list(projectId);
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
