package com.deployforge.api.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateServiceRequest(
        @NotBlank
        @Size(max = 160)
        String name,
        @NotBlank
        @Pattern(regexp = "^[a-z0-9][a-z0-9-]*$")
        String slug,
        String repositoryUrl,
        ServiceTier serviceTier,
        RuntimeKind runtimeKind,
        com.deployforge.api.project.LifecycleStatus lifecycleStatus
) {
}
