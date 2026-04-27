package com.deployforge.api.gate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

public record EvaluateGatesRequest(
        List<UUID> gateDefinitionIds,
        @NotBlank String requestedBy,
        Map<String, Double> metrics
) {
}
