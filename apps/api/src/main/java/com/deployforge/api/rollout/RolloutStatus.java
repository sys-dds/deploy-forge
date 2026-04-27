package com.deployforge.api.rollout;

public enum RolloutStatus {
    NOT_STARTED,
    RUNNING,
    WAITING_FOR_GATES,
    PAUSED,
    SUCCEEDED,
    FAILED,
    ABORTED,
    ROLLBACK_RECOMMENDED
}
