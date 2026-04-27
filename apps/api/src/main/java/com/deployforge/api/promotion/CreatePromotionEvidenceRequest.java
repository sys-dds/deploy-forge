package com.deployforge.api.promotion;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreatePromotionEvidenceRequest(
        @NotNull UUID sourceEnvironmentId,
        UUID targetEnvironmentId,
        @NotNull PromotionEvidenceType evidenceType,
        @NotBlank String evidenceRef,
        @NotBlank String recordedBy,
        @NotBlank String reason,
        JsonNode metadata
) {
}
