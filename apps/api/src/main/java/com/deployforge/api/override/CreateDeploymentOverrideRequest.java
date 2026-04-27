package com.deployforge.api.override;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateDeploymentOverrideRequest(
        @NotNull DeploymentOverrideType overrideType,
        @NotBlank String actor,
        @NotBlank String reason,
        @NotBlank String riskAcknowledgement,
        JsonNode metadata
) {
}
