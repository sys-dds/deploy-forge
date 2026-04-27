package com.deployforge.api.gate;

public enum GateAttemptStatus {
    PENDING,
    RUNNING,
    PASSED,
    FAILED,
    TIMED_OUT,
    SKIPPED,
    OVERRIDDEN
}
