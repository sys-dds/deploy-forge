package com.deployforge.api.gate;

import java.util.List;
import java.util.UUID;

public record GateEvidenceResponse(
        UUID deploymentPlanId,
        String overallStatus,
        boolean requiredGatesPassed,
        List<GateEvidenceAttemptResponse> attempts
) {
}
