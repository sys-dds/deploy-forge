package com.deployforge.api.project;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        String slug,
        String description,
        OffsetDateTime createdAt
) {
}
