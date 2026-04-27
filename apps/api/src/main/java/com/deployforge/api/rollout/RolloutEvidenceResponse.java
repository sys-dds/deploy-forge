package com.deployforge.api.rollout;

import java.util.List;
import java.util.UUID;

public record RolloutEvidenceResponse(
        UUID rolloutId,
        UUID deploymentPlanId,
        String strategy,
        String status,
        Integer currentWaveNumber,
        List<RolloutWaveEvidenceResponse> waves,
        RollbackRecommendationResponse rollbackRecommendation,
        String recommendedNextAction
) {
}
