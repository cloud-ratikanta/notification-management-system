package com.interview.assessment.notification.strategy;

import com.interview.assessment.notification.domain.enums.Channel;

import java.util.UUID;

public record DeliveryCommand(
        UUID deliveryId,
        UUID notificationId,
        String recipientId,
        String recipientEmail,
        String recipientPhone,
        String recipientSlackTarget,
        Channel channel,
        int attemptNo
) {
}

