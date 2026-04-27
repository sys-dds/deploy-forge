package com.deployforge.api.policy;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.deployforge.api.plan.RiskLevel;

public record ProtectedEnvironmentPolicyResponse(
        UUID id,
        UUID projectId,
        UUID environmentId,
        boolean requireApproval,
        int requiredApprovalCount,
        boolean requirePromotionEvidence,
        boolean allowEmergencyOverride,
        RiskLevel maxRiskWithoutOverride,
        String createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
