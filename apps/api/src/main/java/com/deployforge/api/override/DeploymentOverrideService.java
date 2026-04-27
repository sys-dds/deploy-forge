package com.deployforge.api.override;

import java.util.List;
import java.util.UUID;

import com.deployforge.api.event.DeploymentIntentEventRepository;
import com.deployforge.api.event.DeploymentIntentEventType;
import com.deployforge.api.plan.DeploymentPlanResponse;
import com.deployforge.api.plan.DeploymentPlanService;
import com.deployforge.api.plan.DeploymentPlanStatus;
import com.deployforge.api.shared.ApiException;
import com.deployforge.api.shared.Jsonb;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class DeploymentOverrideService {
    private final DeploymentPlanService planService;
    private final DeploymentOverrideRepository repository;
    private final DeploymentIntentEventRepository eventRepository;

    public DeploymentOverrideService(DeploymentPlanService planService, DeploymentOverrideRepository repository,
            DeploymentIntentEventRepository eventRepository) {
        this.planService = planService;
        this.repository = repository;
        this.eventRepository = eventRepository;
    }

    public DeploymentOverrideResponse create(UUID projectId, UUID planId, CreateDeploymentOverrideRequest request) {
        DeploymentPlanResponse plan = planService.get(projectId, planId);
        if (plan.status() == DeploymentPlanStatus.CANCELLED || plan.status() == DeploymentPlanStatus.ABORTED) {
            throw new ApiException(HttpStatus.CONFLICT, "OVERRIDE_BLOCKED", "Cancelled or aborted plans cannot receive overrides");
        }
        DeploymentOverrideResponse override = repository.create(projectId, planId, request);
        eventRepository.record(projectId, planId, plan.serviceId(), plan.targetEnvironmentId(), plan.artifactId(),
                DeploymentIntentEventType.OVERRIDE_RECORDED, request.actor(), request.reason(),
                Jsonb.object().put("overrideType", request.overrideType().name()));
        return override;
    }

    public List<DeploymentOverrideResponse> list(UUID projectId, UUID planId) {
        planService.get(projectId, planId);
        return repository.list(projectId, planId);
    }
}
