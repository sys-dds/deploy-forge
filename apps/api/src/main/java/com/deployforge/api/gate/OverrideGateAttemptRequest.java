package com.deployforge.api.gate;

import jakarta.validation.constraints.NotBlank;

public record OverrideGateAttemptRequest(
        @NotBlank String overriddenBy,
        @NotBlank String reason,
        @NotBlank String riskAcknowledgement
) {
}
