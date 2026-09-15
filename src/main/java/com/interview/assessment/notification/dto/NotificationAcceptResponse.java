package com.interview.assessment.notification.dto;

import com.interview.assessment.notification.domain.enums.Channel;
import com.interview.assessment.notification.domain.enums.NotificationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NotificationAcceptResponse(
        UUID notificationId,
        NotificationStatus status,
        boolean duplicate,
        List<Channel> selectedChannels,
        Instant createdAt
) {
}

