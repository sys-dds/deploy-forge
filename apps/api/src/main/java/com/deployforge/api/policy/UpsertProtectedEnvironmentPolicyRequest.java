package com.deployforge.api.policy;

import com.deployforge.api.plan.RiskLevel;
import jakarta.validation.constraints.NotBlank;

public record UpsertProtectedEnvironmentPolicyRequest(
        Boolean requireApproval,
        Integer requiredApprovalCount,
        Boolean requirePromotionEvidence,
        Boolean allowEmergencyOverride,
        RiskLevel maxRiskWithoutOverride,
        @NotBlank String createdBy
) {
}
