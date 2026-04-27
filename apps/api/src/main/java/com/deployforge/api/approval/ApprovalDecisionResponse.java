package com.deployforge.api.approval;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ApprovalDecisionResponse(
        UUID id,
        UUID approvalRequestId,
        ApprovalDecision decision,
        String decidedBy,
        String reason,
        OffsetDateTime createdAt
) {
}
