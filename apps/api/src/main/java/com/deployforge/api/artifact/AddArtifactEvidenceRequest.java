package com.deployforge.api.artifact;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddArtifactEvidenceRequest(
        @NotNull EvidenceType evidenceType,
        @NotBlank String evidenceRef,
        String evidenceSha,
        @NotBlank String addedBy,
        String reason,
        JsonNode metadata
) {
}
