package com.interview.assessment.notification.strategy;

import com.interview.assessment.notification.domain.enums.FailureClass;

/**
 * Result of a delivery attempt. Optional `retryAfterSeconds` can be set for RATE_LIMIT classes.
 */
public record DeliveryResult(
        boolean success,
        FailureClass failureClass,
        String safeDetail,
        Long retryAfterSeconds
) {
    public static DeliveryResult ok() {
        return new DeliveryResult(true, null, null, null);
    }

    public static DeliveryResult failed(FailureClass failureClass, String safeDetail) {
        return new DeliveryResult(false, failureClass, safeDetail, null);
    }

    public static DeliveryResult failedWithRetry(FailureClass failureClass, String safeDetail, Long retryAfterSeconds) {
        return new DeliveryResult(false, failureClass, safeDetail, retryAfterSeconds);
    }
}

