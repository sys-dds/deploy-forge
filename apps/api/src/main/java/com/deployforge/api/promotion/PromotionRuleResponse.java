package com.deployforge.api.promotion;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PromotionRuleResponse(
        UUID id,
        UUID projectId,
        UUID targetEnvironmentId,
        UUID requiredSourceEnvironmentId,
        boolean requiresSuccessfulSourceDeployment,
        boolean requiresApproval,
        boolean allowOverride,
        String createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
