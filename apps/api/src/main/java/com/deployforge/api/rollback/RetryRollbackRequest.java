package com.deployforge.api.rollback;

import jakarta.validation.constraints.NotBlank;

public record RetryRollbackRequest(
        @NotBlank String requestedBy,
        @NotBlank String reason
) {
}
