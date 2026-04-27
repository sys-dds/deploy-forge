package com.deployforge.api.approval;

import jakarta.validation.constraints.NotBlank;

public record ExpireApprovalRequestRequest(
        @NotBlank String actor,
        @NotBlank String reason
) {
}
