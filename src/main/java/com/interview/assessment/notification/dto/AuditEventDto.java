package com.interview.assessment.notification.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditEventDto(
        UUID id,
        UUID notificationId,
        UUID deliveryId,
        String eventType,
        String payloadJson,
        Instant createdAt
) {
}

