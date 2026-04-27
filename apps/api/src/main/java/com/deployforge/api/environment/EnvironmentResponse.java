package com.deployforge.api.environment;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EnvironmentResponse(
        UUID id,
        UUID projectId,
        String name,
        EnvironmentType environmentType,
        boolean protectedEnvironment,
        int sortOrder,
        OffsetDateTime createdAt
) {
}
