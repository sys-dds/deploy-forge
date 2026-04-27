package com.deployforge.api.gate;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;

public record RerunGateAttemptRequest(
        @NotBlank String requestedBy,
        @NotBlank String reason,
        Map<String, Double> metrics
) {
}
