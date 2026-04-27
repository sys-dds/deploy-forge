package com.deployforge.api.rollback;

import java.util.List;
import java.util.UUID;

import com.deployforge.api.rollout.RollbackRecommendationResponse;
import com.deployforge.api.state.EnvironmentDeploymentStateResponse;

public record FailedRolloutRecoveryResponse(
        UUID rolloutId,
        String status,
        String recoveryStatus,
        String recommendedAction,
        RollbackRecommendationResponse rollbackRecommendation,
        RollbackExecutionResponse rollbackExecution,
        EnvironmentDeploymentStateResponse environmentState,
        List<RecoveryCheckResponse> checks
) {
}
