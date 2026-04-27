package com.deployforge.api.rollback;

public record RecoveryCheckResponse(
        String code,
        String status,
        String message
) {
}
