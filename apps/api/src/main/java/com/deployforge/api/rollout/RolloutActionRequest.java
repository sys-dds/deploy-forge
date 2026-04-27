package com.deployforge.api.rollout;

import jakarta.validation.constraints.NotBlank;

public record RolloutActionRequest(
        @NotBlank String actor,
        @NotBlank String reason
) {
}
