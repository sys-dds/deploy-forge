package com.deployforge.api.approval;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateApprovalDecisionRequest(
        @NotNull ApprovalDecision decision,
        @NotBlank String decidedBy,
        @NotBlank String reason
) {
}
