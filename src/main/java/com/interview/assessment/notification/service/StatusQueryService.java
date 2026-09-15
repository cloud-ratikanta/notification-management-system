package com.interview.assessment.notification.service;

import com.interview.assessment.notification.domain.enums.NotificationStatus;
import com.interview.assessment.notification.domain.enums.DeliveryStatus;
import com.interview.assessment.notification.dto.DeliveryStatusDto;
import com.interview.assessment.notification.dto.NotificationAcceptResponse;
import com.interview.assessment.notification.dto.NotificationStatusResponse;
import com.interview.assessment.notification.exception.ResourceNotFoundException;
import com.interview.assessment.notification.persistence.DeliveryRepository;
import com.interview.assessment.notification.persistence.NotificationRepository;
import org.springframework.stereotype.Service;

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

        List<DeliveryStatusDto> deliveries = deliveryRepository.findByNotificationId(notificationId).stream()
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

        boolean anyPending = deliveries.stream().anyMatch(d -> d.status() == DeliveryStatus.PENDING || d.status() == DeliveryStatus.IN_FLIGHT);
        NotificationStatus status = anyPending ? NotificationStatus.IN_PROGRESS : notification.status();

        return new NotificationStatusResponse(
                notification.id(),
                notification.sourceSystem(),
                notification.eventId(),
                status,
                false,
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
}

