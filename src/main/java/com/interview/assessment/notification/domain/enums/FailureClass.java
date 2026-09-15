package com.interview.assessment.notification.domain.enums;

public enum FailureClass {
    TRANSIENT_PROVIDER_FAILURE,
    TIMEOUT,
    RATE_LIMIT,
    PERMANENT_PROVIDER_REJECTION,
    INVALID_RECIPIENT,
    AUTH_ERROR,
    UNKNOWN
}

