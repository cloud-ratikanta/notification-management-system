package com.interview.assessment.notification.dto;

import com.interview.assessment.notification.domain.enums.Channel;
import com.interview.assessment.notification.domain.enums.Priority;
import com.interview.assessment.notification.domain.enums.Severity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SubmitNotificationRequest(
        UUID notificationId,
        @NotBlank String sourceSystem,
        @NotBlank String eventId,
        String correlationId,
        @NotBlank String type,
        @NotNull Severity severity,
        @NotNull Priority priority,
        String title,
        String body,
        String templateKey,
        Map<String, String> templateParams,
        @NotEmpty List<@Valid RecipientDto> recipients,
        List<Channel> requestedChannels,
        Instant scheduleAt,
        Instant expiresAt
) {
}

