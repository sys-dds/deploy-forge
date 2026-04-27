package com.deployforge.api.plan;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public record DeploymentPlanResponse(
        UUID id,
        UUID projectId,
        UUID serviceId,
        UUID artifactId,
        UUID targetEnvironmentId,
        DeploymentStrategy strategy,
        DeploymentPlanStatus status,
        RiskLevel riskLevel,
        String reason,
        String requestedBy,
        String idempotencyKey,
        String requestHash,
        JsonNode evidenceSnapshot,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime cancelledAt,
        String cancelledBy,
        String cancelReason
) {
}
