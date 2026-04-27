package com.deployforge.api.gate;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public record GateEvidenceAttemptResponse(
        UUID gateDefinitionId,
        String gateName,
        GateType gateType,
        boolean required,
        int attemptNumber,
        GateAttemptStatus status,
        JsonNode observed,
        String resultSummary,
        String failureReason
) {
}
