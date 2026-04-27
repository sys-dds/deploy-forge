package com.deployforge.api.rollout;

import jakarta.validation.constraints.NotBlank;

public record StartRolloutRequest(
        @NotBlank String startedBy,
        @NotBlank String reason
) {
}
