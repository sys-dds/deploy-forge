package com.deployforge.api.readiness;

import java.util.List;
import java.util.UUID;

public record DeploymentReadinessResponse(
        UUID deploymentPlanId,
        boolean readyToStart,
        List<DeploymentReadinessCheckResponse> checks,
        String recommendedAction
) {
}
