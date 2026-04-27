package com.deployforge.api.plan;

import jakarta.validation.constraints.NotBlank;

public record CancelDeploymentPlanRequest(
        @NotBlank String cancelledBy,
        @NotBlank String reason
) {
}
