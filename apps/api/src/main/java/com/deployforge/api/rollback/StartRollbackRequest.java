package com.deployforge.api.rollback;

import jakarta.validation.constraints.NotBlank;

public record StartRollbackRequest(
        @NotBlank String startedBy,
        @NotBlank String reason
) {
}
