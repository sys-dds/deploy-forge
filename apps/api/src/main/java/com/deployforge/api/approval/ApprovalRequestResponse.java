package com.deployforge.api.approval;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ApprovalRequestResponse(
        UUID id,
        UUID projectId,
        UUID deploymentPlanId,
        UUID environmentId,
        ApprovalStatus status,
        int requiredApprovalCount,
        int approvedCount,
        String requestedBy,
        String reason,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
