package com.deployforge.api.plan;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateDeploymentPlanRequest(
        @NotNull UUID serviceId,
        @NotNull UUID artifactId,
        @NotNull UUID targetEnvironmentId,
        @NotNull DeploymentStrategy strategy,
        @NotBlank String reason,
        @NotBlank String requestedBy
) {
}
