package com.deployforge.api.readiness;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/deployment-plans/{planId}/readiness")
public class DeploymentReadinessController {
    private final DeploymentReadinessService service;

    public DeploymentReadinessController(DeploymentReadinessService service) {
        this.service = service;
    }

    @GetMapping
    public DeploymentReadinessResponse get(@PathVariable UUID projectId, @PathVariable UUID planId) {
        return service.get(projectId, planId);
    }
}
