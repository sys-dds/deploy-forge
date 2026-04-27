package com.deployforge.api.promotion;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

public record UpsertPromotionRuleRequest(
        UUID requiredSourceEnvironmentId,
        Boolean requiresSuccessfulSourceDeployment,
        Boolean requiresApproval,
        Boolean allowOverride,
        @NotBlank String createdBy
) {
}
