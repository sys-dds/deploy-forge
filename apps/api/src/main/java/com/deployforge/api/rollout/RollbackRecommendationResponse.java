package com.deployforge.api.rollout;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public record RollbackRecommendationResponse(
        UUID id,
        UUID projectId,
        UUID rolloutExecutionId,
        UUID deploymentPlanId,
        UUID serviceId,
        UUID environmentId,
        UUID failedArtifactId,
        UUID recommendedArtifactId,
        RollbackRecommendationStatus recommendationStatus,
        String reason,
        JsonNode metadata,
        OffsetDateTime createdAt,
        OffsetDateTime acknowledgedAt,
        String acknowledgedBy,
        String acknowledgementReason
) {
}
