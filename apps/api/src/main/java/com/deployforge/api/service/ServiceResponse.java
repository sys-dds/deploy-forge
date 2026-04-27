package com.deployforge.api.service;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ServiceResponse(
        UUID id,
        UUID projectId,
        String name,
        String slug,
        String repositoryUrl,
        OffsetDateTime createdAt
) {
}
