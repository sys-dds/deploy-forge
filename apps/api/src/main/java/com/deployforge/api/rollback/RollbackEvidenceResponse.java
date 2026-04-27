package com.deployforge.api.rollback;

import java.util.List;
import java.util.UUID;

import com.deployforge.api.event.DeploymentIntentEventResponse;
import com.deployforge.api.gate.GateEvidenceResponse;
import com.deployforge.api.state.EnvironmentDeploymentStateResponse;

public record RollbackEvidenceResponse(
        UUID rollbackExecutionId,
        UUID rollbackRecommendationId,
        UUID rolloutExecutionId,
        UUID deploymentPlanId,
        UUID failedArtifactId,
        UUID targetArtifactId,
        String status,
        GateEvidenceResponse gateEvidence,
        EnvironmentDeploymentStateResponse environmentState,
        String recoveryRecommendation,
        String manualInterventionReason,
        List<DeploymentIntentEventResponse> eventSummary
) {
}
