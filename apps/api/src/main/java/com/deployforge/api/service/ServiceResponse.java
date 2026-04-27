package com.deployforge.api.service;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.deployforge.api.project.LifecycleStatus;

public record ServiceResponse(
        UUID id,
        UUID projectId,
        String name,
        String slug,
        String repositoryUrl,
        ServiceTier serviceTier,
        RuntimeKind runtimeKind,
        LifecycleStatus lifecycleStatus,
        OffsetDateTime createdAt
) {
}
