package com.deployforge.api.rollback;

import jakarta.validation.constraints.NotBlank;

public record MarkManualInterventionRequest(
        @NotBlank String actor,
        @NotBlank String reason,
        String riskAcknowledgement
) {
}
