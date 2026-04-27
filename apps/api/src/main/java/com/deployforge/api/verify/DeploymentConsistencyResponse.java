package com.deployforge.api.verify;

import java.util.List;
import java.util.UUID;

public record DeploymentConsistencyResponse(
        UUID projectId,
        boolean consistent,
        List<ConsistencyViolationResponse> violations
) {
}
