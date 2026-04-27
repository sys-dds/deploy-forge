package com.deployforge.api.plan;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/deployment-plans")
public class DeploymentPlanController {

    private final DeploymentPlanService planService;

    public DeploymentPlanController(DeploymentPlanService planService) {
        this.planService = planService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeploymentPlanResponse create(@PathVariable UUID projectId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateDeploymentPlanRequest request) {
        return planService.create(projectId, idempotencyKey, request);
    }

    @GetMapping
    public List<DeploymentPlanResponse> list(@PathVariable UUID projectId) {
        return planService.list(projectId);
    }

    @GetMapping("/{planId}")
    public DeploymentPlanResponse get(@PathVariable UUID projectId, @PathVariable UUID planId) {
        return planService.get(projectId, planId);
    }

    @PostMapping("/{planId}/cancel")
    public DeploymentPlanResponse cancel(@PathVariable UUID projectId, @PathVariable UUID planId,
            @Valid @RequestBody CancelDeploymentPlanRequest request) {
        return planService.cancel(projectId, planId, request);
    }
}
