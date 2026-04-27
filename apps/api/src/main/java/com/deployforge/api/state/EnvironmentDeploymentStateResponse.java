package com.deployforge.api.state;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EnvironmentDeploymentStateResponse(
        UUID projectId,
        UUID serviceId,
        UUID environmentId,
        UUID currentArtifactId,
        UUID lastDeploymentPlanId,
        String stateStatus,
        OffsetDateTime updatedAt
) {
}
