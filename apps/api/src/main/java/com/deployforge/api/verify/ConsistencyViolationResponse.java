package com.deployforge.api.verify;

import java.util.UUID;

public record ConsistencyViolationResponse(
        String code,
        String severity,
        String message,
        UUID relatedId,
        String recommendedAction
) {
}
