package com.deployforge.api.promotion;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public record PromotionEvidenceResponse(
        UUID id,
        UUID projectId,
        UUID serviceId,
        UUID artifactId,
        UUID sourceEnvironmentId,
        UUID targetEnvironmentId,
        PromotionEvidenceType evidenceType,
        String evidenceRef,
        String recordedBy,
        String reason,
        JsonNode metadata,
        OffsetDateTime createdAt
) {
}
