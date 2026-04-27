package com.deployforge.api.gate;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateGateDefinitionRequest(
        UUID environmentId,
        @NotBlank String name,
        @NotNull GateType gateType,
        Boolean required,
        Boolean enabled,
        Integer timeoutSeconds,
        JsonNode config,
        @NotBlank String createdBy
) {
}
