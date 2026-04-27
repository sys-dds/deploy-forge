package com.deployforge.api.policy;

import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/environments/{environmentId}/protection-policy")
public class ProtectedEnvironmentPolicyController {
    private final ProtectedEnvironmentPolicyService service;

    public ProtectedEnvironmentPolicyController(ProtectedEnvironmentPolicyService service) {
        this.service = service;
    }

    @PutMapping
    public ProtectedEnvironmentPolicyResponse upsert(@PathVariable UUID projectId, @PathVariable UUID environmentId,
            @Valid @RequestBody UpsertProtectedEnvironmentPolicyRequest request) {
        return service.upsert(projectId, environmentId, request);
    }

    @GetMapping
    public ProtectedEnvironmentPolicyResponse get(@PathVariable UUID projectId, @PathVariable UUID environmentId) {
        return service.get(projectId, environmentId);
    }
}
