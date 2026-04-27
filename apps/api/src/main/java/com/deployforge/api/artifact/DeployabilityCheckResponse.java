package com.deployforge.api.artifact;

public record DeployabilityCheckResponse(
        String code,
        String status,
        String message
) {
}
