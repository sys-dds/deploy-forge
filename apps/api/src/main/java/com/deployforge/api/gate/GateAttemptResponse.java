package com.deployforge.api.gate;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public record GateAttemptResponse(
        UUID id,
        UUID projectId,
        UUID deploymentPlanId,
        UUID gateDefinitionId,
        int attemptNumber,
        GateAttemptStatus status,
        JsonNode observed,
        String resultSummary,
        String failureReason,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime overriddenAt,
        String overriddenBy,
        String overrideReason
) {
}
