package com.aimv.domain.chain;

public enum StageRunStatus {
    CREATED,
    EXECUTING,
    SUCCEEDED,
    WAITING_USER,
    WAITING_CAPABILITY,
    WAITING_REVIEW,
    FAILED,
    CANCELLED
}
