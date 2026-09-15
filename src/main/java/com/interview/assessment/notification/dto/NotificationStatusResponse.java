package com.interview.assessment.notification.dto;

import com.interview.assessment.notification.domain.enums.Channel;
import com.interview.assessment.notification.domain.enums.NotificationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NotificationStatusResponse(
        UUID notificationId,
        String sourceSystem,
        String eventId,
        NotificationStatus status,
        boolean partialSuccess,
        List<Channel> selectedChannels,
        Instant createdAt,
        Instant updatedAt,
        List<DeliveryStatusDto> deliveries
) {
}

