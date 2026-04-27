package com.deployforge.api.rollback;

import jakarta.validation.constraints.NotBlank;

public record CompleteRollbackRequest(
        @NotBlank String actor,
        @NotBlank String reason
) {
}
