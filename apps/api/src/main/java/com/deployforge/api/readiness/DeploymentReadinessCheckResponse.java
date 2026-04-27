package com.deployforge.api.readiness;

public record DeploymentReadinessCheckResponse(
        String code,
        String status,
        String message
) {
}
