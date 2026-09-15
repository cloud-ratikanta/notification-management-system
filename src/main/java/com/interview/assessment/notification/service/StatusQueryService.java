package com.interview.assessment.notification.service;

import com.interview.assessment.notification.domain.enums.DeliveryStatus;
import com.interview.assessment.notification.domain.enums.NotificationStatus;
import com.interview.assessment.notification.dto.DeliveryStatusDto;
import com.interview.assessment.notification.dto.NotificationAcceptResponse;
import com.interview.assessment.notification.dto.NotificationStatusResponse;
import com.interview.assessment.notification.exception.ResourceNotFoundException;
import com.interview.assessment.notification.persistence.DeliveryRepository;
import com.interview.assessment.notification.persistence.NotificationRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class StatusQueryService {

    private final NotificationRepository notificationRepository;
    private final DeliveryRepository deliveryRepository;

    public StatusQueryService(NotificationRepository notificationRepository, DeliveryRepository deliveryRepository) {
        this.notificationRepository = notificationRepository;
        this.deliveryRepository = deliveryRepository;
    }

    public NotificationStatusResponse getStatus(UUID notificationId) {
        NotificationRepository.NotificationRow notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));

        List<DeliveryRepository.DeliveryRow> deliveryRows = deliveryRepository.findByNotificationId(notificationId);
        List<DeliveryStatusDto> deliveries = deliveryRows.stream()
                .map(d -> new DeliveryStatusDto(
                        d.id(),
                        d.recipientId(),
                        d.channel(),
                        d.status(),
                        d.attemptCount(),
                        d.lastAttemptAt(),
                        d.nextAttemptAt(),
                        d.lastErrorClass()))
                .toList();

        RollUp rollUp = rollUp(notification, deliveryRows, Instant.now());

        return new NotificationStatusResponse(
                notification.id(),
                notification.sourceSystem(),
                notification.eventId(),
                rollUp.status(),
                rollUp.partialSuccess(),
                notification.selectedChannels(),
                notification.createdAt(),
                notification.updatedAt(),
                deliveries
        );
    }

    public NotificationAcceptResponse getAcceptView(UUID notificationId, boolean duplicate) {
        NotificationRepository.NotificationRow notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));
        return new NotificationAcceptResponse(
                notification.id(),
                notification.status(),
                duplicate,
                notification.selectedChannels(),
                notification.createdAt()
        );
    }

    /**
     * LLD §4.3 overall status roll-up (prototype).
     */
    private RollUp rollUp(NotificationRepository.NotificationRow notification,
                          List<DeliveryRepository.DeliveryRow> deliveries,
                          Instant now) {
        if (deliveries.isEmpty()) {
            return new RollUp(notification.status(), false);
        }

        boolean expiredWindow = notification.expiresAt() != null && !notification.expiresAt().isAfter(now);
        boolean anySucceeded = deliveries.stream().anyMatch(d -> d.status() == DeliveryStatus.SUCCEEDED);
        if (expiredWindow && !anySucceeded) {
            return new RollUp(NotificationStatus.EXPIRED, false);
        }

        boolean anyOpen = deliveries.stream().anyMatch(d ->
                d.status() == DeliveryStatus.PENDING
                        || d.status() == DeliveryStatus.IN_FLIGHT
                        || d.status() == DeliveryStatus.RETRY_SCHEDULED);
        if (anyOpen) {
            return new RollUp(NotificationStatus.IN_PROGRESS, false);
        }

        boolean allSucceeded = deliveries.stream().allMatch(d -> d.status() == DeliveryStatus.SUCCEEDED);
        if (allSucceeded) {
            return new RollUp(NotificationStatus.COMPLETED, false);
        }

        boolean allFailed = deliveries.stream().allMatch(d ->
                d.status() == DeliveryStatus.FAILED_TERMINAL || d.status() == DeliveryStatus.EXPIRED);
        if (allFailed) {
            return new RollUp(NotificationStatus.FAILED, false);
        }

        if (anySucceeded) {
            return new RollUp(NotificationStatus.COMPLETED, true);
        }

        return new RollUp(notification.status(), false);
    }

    private record RollUp(NotificationStatus status, boolean partialSuccess) {
    }
}
