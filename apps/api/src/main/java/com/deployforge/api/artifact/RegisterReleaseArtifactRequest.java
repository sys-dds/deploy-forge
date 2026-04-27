package com.deployforge.api.artifact;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegisterReleaseArtifactRequest(
        @NotBlank String version,
        @NotBlank String gitSha,
        @NotBlank @Pattern(regexp = "^sha256:.+") String imageDigest,
        String buildNumber,
        String sourceBranch,
        String commitMessage,
        @NotBlank String createdBy,
        JsonNode metadata,
        ArtifactReadinessStatus readinessStatus
) {
}
