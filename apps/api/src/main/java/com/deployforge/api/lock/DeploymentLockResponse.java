package com.deployforge.api.lock;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DeploymentLockResponse(
        UUID id,
        UUID projectId,
        UUID serviceId,
        UUID environmentId,
        UUID deploymentPlanId,
        DeploymentLockStatus status,
        String lockOwner,
        String reason,
        long fencingToken,
        OffsetDateTime acquiredAt,
        OffsetDateTime expiresAt,
        OffsetDateTime releasedAt,
        String releasedBy,
        String releaseReason
) {
}
