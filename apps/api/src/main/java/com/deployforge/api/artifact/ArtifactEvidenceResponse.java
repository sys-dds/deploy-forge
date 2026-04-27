package com.deployforge.api.artifact;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public record ArtifactEvidenceResponse(
        UUID id,
        UUID artifactId,
        EvidenceType evidenceType,
        String evidenceRef,
        String evidenceSha,
        String addedBy,
        String reason,
        JsonNode metadata,
        OffsetDateTime createdAt
) {
}
