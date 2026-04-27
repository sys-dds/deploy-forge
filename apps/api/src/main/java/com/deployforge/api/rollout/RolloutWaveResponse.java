package com.deployforge.api.rollout;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RolloutWaveResponse(
        UUID id,
        UUID rolloutExecutionId,
        UUID projectId,
        UUID deploymentPlanId,
        int waveNumber,
        int trafficPercentage,
        RolloutWaveStatus status,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        String failureReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
