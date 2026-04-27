package com.deployforge.api.rollback;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RollbackExecutionResponse(
        UUID id,
        UUID projectId,
        UUID rollbackRecommendationId,
        UUID rolloutExecutionId,
        UUID deploymentPlanId,
        UUID serviceId,
        UUID environmentId,
        UUID failedArtifactId,
        UUID targetArtifactId,
        RollbackStatus status,
        String startedBy,
        String reason,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        String failureReason,
        int retryCount,
        String manualInterventionReason,
        String successActor,
        String successReason,
        String failureActor,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
