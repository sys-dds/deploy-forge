package com.deployforge.api.rollout;

import com.deployforge.api.gate.GateEvidenceResponse;

public record RolloutWaveEvidenceResponse(
        int waveNumber,
        int trafficPercentage,
        String status,
        GateEvidenceResponse gateEvidence
) {
}
