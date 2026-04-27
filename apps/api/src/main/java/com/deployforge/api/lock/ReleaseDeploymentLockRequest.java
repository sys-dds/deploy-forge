package com.deployforge.api.lock;

import jakarta.validation.constraints.NotBlank;

public record ReleaseDeploymentLockRequest(
        @NotBlank String releasedBy,
        @NotBlank String reason
) {
}
