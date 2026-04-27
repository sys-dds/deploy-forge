package com.deployforge.api.rollout;

import jakarta.validation.constraints.NotBlank;

public record AcknowledgeRollbackRecommendationRequest(
        @NotBlank String acknowledgedBy,
        @NotBlank String reason
) {
}
