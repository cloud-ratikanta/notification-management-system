package com.interview.assessment.notification.dto;

import com.interview.assessment.notification.domain.enums.Channel;
import com.interview.assessment.notification.domain.enums.DeliveryStatus;
import com.interview.assessment.notification.domain.enums.FailureClass;

import java.time.Instant;
import java.util.UUID;

public record DeliveryStatusDto(
        UUID deliveryId,
        String recipientId,
        Channel channel,
        DeliveryStatus status,
        int attemptCount,
        Instant lastAttemptAt,
        Instant nextAttemptAt,
        FailureClass lastErrorClass
) {
}

