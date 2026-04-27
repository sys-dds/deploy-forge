package com.deployforge.api.rollback;

import java.util.List;
import java.util.UUID;

import com.deployforge.api.event.DeploymentIntentEventResponse;

public record RecoveryTimelineResponse(
        UUID rolloutId,
        List<DeploymentIntentEventResponse> events
) {
}
