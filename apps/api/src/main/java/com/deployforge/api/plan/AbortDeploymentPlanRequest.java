package com.deployforge.api.plan;

import jakarta.validation.constraints.NotBlank;

public record AbortDeploymentPlanRequest(
        @NotBlank String abortedBy,
        @NotBlank String reason
) {
}
