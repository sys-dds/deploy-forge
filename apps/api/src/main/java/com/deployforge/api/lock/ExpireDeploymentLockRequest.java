package com.deployforge.api.lock;

import jakarta.validation.constraints.NotBlank;

public record ExpireDeploymentLockRequest(
        @NotBlank String actor,
        @NotBlank String reason
) {
}
