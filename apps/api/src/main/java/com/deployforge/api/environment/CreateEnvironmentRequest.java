package com.deployforge.api.environment;

public record CreateEnvironmentRequest(
        String name,
        String environmentType,
        boolean protectedEnvironment,
        int sortOrder
) {
}
