package com.deployforge.api.event;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public record DeploymentIntentEventResponse(
        UUID id,
        UUID projectId,
        UUID deploymentPlanId,
        UUID serviceId,
        UUID environmentId,
        UUID artifactId,
        DeploymentIntentEventType eventType,
        String actor,
        String reason,
        JsonNode metadata,
        OffsetDateTime createdAt
) {
}
