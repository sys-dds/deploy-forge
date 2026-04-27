package com.deployforge.api.override;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public record DeploymentOverrideResponse(
        UUID id,
        UUID projectId,
        UUID deploymentPlanId,
        DeploymentOverrideType overrideType,
        String actor,
        String reason,
        String riskAcknowledgement,
        JsonNode metadata,
        OffsetDateTime createdAt
) {
}
