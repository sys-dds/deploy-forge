package com.deployforge.api.lock;

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
@RequestMapping("/api/v1/projects/{projectId}")
public class DeploymentLockController {
    private final DeploymentLockService service;

    public DeploymentLockController(DeploymentLockService service) {
        this.service = service;
    }

    @PostMapping("/deployment-plans/{planId}/locks/acquire")
    public DeploymentLockResponse acquire(@PathVariable UUID projectId, @PathVariable UUID planId,
            @Valid @RequestBody AcquireDeploymentLockRequest request) {
        return service.acquire(projectId, planId, request);
    }

    @GetMapping("/deployment-locks")
    public List<DeploymentLockResponse> list(@PathVariable UUID projectId) {
        return service.list(projectId);
    }

    @PostMapping("/deployment-locks/{lockId}/release")
    public DeploymentLockResponse release(@PathVariable UUID projectId, @PathVariable UUID lockId,
            @Valid @RequestBody ReleaseDeploymentLockRequest request) {
        return service.release(projectId, lockId, request);
    }

    @PostMapping("/deployment-locks/{lockId}/expire")
    public DeploymentLockResponse expire(@PathVariable UUID projectId, @PathVariable UUID lockId,
            @Valid @RequestBody ExpireDeploymentLockRequest request) {
        return service.expire(projectId, lockId, request);
    }

    @PostMapping("/deployment-locks/{lockId}/release-stale")
    public DeploymentLockResponse releaseStale(@PathVariable UUID projectId, @PathVariable UUID lockId,
            @Valid @RequestBody ReleaseDeploymentLockRequest request) {
        return service.releaseStale(projectId, lockId, request);
    }
}
