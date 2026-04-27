package com.deployforge.api.environment;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.deployforge.api.project.LifecycleStatus;

public record EnvironmentResponse(
        UUID id,
        UUID projectId,
        String name,
        EnvironmentType environmentType,
        boolean protectedEnvironment,
        int sortOrder,
        String externalTargetId,
        boolean requiresApproval,
        LifecycleStatus lifecycleStatus,
        OffsetDateTime createdAt
) {
}
