package com.deployforge.api.rollback;

public enum RollbackStatus {
    RUNNING,
    WAITING_FOR_GATES,
    SUCCEEDED,
    FAILED,
    MANUAL_INTERVENTION_REQUIRED,
    ABORTED
}
