package com.deployforge.api.artifact;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public record ReleaseArtifactResponse(
        UUID id,
        UUID projectId,
        UUID serviceId,
        String version,
        String gitSha,
        String imageDigest,
        String buildNumber,
        String sourceBranch,
        String commitMessage,
        String createdBy,
        JsonNode metadata,
        ArtifactReadinessStatus readinessStatus,
        OffsetDateTime createdAt
) {
}
