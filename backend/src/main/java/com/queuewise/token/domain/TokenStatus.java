package com.queuewise.token.domain;

public enum TokenStatus {
    WAITING,
    CALLED,
    SERVING,
    COMPLETED,
    SKIPPED,
    NO_SHOW,
    CANCELLED
}
