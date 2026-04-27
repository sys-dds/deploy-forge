package com.deployforge.api.override;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/deployment-plans/{planId}/overrides")
public class DeploymentOverrideController {
    private final DeploymentOverrideService service;

    public DeploymentOverrideController(DeploymentOverrideService service) {
        this.service = service;
    }

    @PostMapping
    public DeploymentOverrideResponse create(@PathVariable UUID projectId, @PathVariable UUID planId,
            @Valid @RequestBody CreateDeploymentOverrideRequest request) {
        return service.create(projectId, planId, request);
    }

    @GetMapping
    public List<DeploymentOverrideResponse> list(@PathVariable UUID projectId, @PathVariable UUID planId) {
        return service.list(projectId, planId);
    }
}
