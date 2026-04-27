package com.deployforge.api.rollout;

public enum RolloutWaveStatus {
    PENDING,
    RUNNING,
    WAITING_FOR_GATES,
    PASSED,
    FAILED,
    SKIPPED,
    ABORTED
}
