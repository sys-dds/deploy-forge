package com.deployforge.api.gate;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public record GateDefinitionResponse(
        UUID id,
        UUID projectId,
        UUID environmentId,
        String name,
        GateType gateType,
        boolean required,
        boolean enabled,
        int timeoutSeconds,
        JsonNode config,
        String createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
