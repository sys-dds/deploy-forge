package com.deployforge.api.rollout;

import java.time.OffsetDateTime;
import java.util.UUID;

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
        OffsetDateTime createdAt,
        OffsetDateTime acknowledgedAt,
        String acknowledgedBy,
        String acknowledgementReason
) {
}
