package com.deployforge.api.rollout;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.deployforge.api.plan.DeploymentStrategy;

public record RolloutExecutionResponse(
        UUID id,
        UUID projectId,
        UUID deploymentPlanId,
        UUID serviceId,
        UUID environmentId,
        UUID artifactId,
        DeploymentStrategy strategy,
        RolloutStatus status,
        String startedBy,
        String reason,
        Integer currentWaveNumber,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime pausedAt,
        String pausedBy,
        String pauseReason,
        OffsetDateTime resumedAt,
        String resumedBy,
        String resumeReason,
        OffsetDateTime abortedAt,
        String abortedBy,
        String abortReason,
        String failureReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
