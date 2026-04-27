package com.deployforge.api.approval;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateApprovalRequestRequest(
        @NotBlank String requestedBy,
        @NotBlank String reason,
        @Min(1) Integer requiredApprovalCount,
        OffsetDateTime expiresAt
) {
}
