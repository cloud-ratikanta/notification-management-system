package com.interview.assessment.notification.strategy;

import com.interview.assessment.notification.domain.enums.Channel;

import java.util.UUID;

public record DeliveryCommand(
        UUID deliveryId,
        UUID notificationId,
        String recipientId,
        Channel channel,
        int attemptNo
) {
}

