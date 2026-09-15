package com.interview.assessment.notification.strategy;

import com.interview.assessment.notification.domain.enums.FailureClass;

public record DeliveryResult(
        boolean success,
        FailureClass failureClass,
        String safeDetail
) {
    public static DeliveryResult ok() {
        return new DeliveryResult(true, null, null);
    }

    public static DeliveryResult failed(FailureClass failureClass, String safeDetail) {
        return new DeliveryResult(false, failureClass, safeDetail);
    }
}

