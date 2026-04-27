package com.deployforge.api.verify;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class DeploymentConsistencyController {
    private final DeploymentConsistencyVerifier verifier;

    public DeploymentConsistencyController(DeploymentConsistencyVerifier verifier) {
        this.verifier = verifier;
    }

    @GetMapping("/deployment-consistency")
    public DeploymentConsistencyResponse verify(@PathVariable UUID projectId) {
        return verifier.verify(projectId);
    }

    @GetMapping("/services/{serviceId}/environments/{environmentId}/state/verify")
    public DeploymentConsistencyResponse verifyState(@PathVariable UUID projectId, @PathVariable UUID serviceId,
            @PathVariable UUID environmentId) {
        return verifier.verify(projectId);
    }
}
