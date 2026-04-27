package com.deployforge.api.state;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EnvironmentDeploymentStateResponse(
        UUID projectId,
        UUID serviceId,
        UUID environmentId,
        UUID currentArtifactId,
        UUID previousStableArtifactId,
        UUID lastDeploymentPlanId,
        UUID lastRolloutExecutionId,
        UUID lastRollbackExecutionId,
        String stateStatus,
        OffsetDateTime updatedAt
) {
}
