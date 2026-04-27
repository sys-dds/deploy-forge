package com.deployforge.api.lock;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AcquireDeploymentLockRequest(
        @NotBlank String lockOwner,
        @NotBlank String reason,
        @Min(1) long ttlSeconds
) {
}
