package com.interview.assessment.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.assessment.notification.domain.ChannelRouter;
import com.interview.assessment.notification.domain.RoutingInput;
import com.interview.assessment.notification.domain.enums.DeliveryStatus;
import com.interview.assessment.notification.domain.enums.NotificationStatus;
import com.interview.assessment.notification.dto.NotificationAcceptResponse;
import com.interview.assessment.notification.dto.RecipientDto;
import com.interview.assessment.notification.dto.SubmitNotificationRequest;
import com.interview.assessment.notification.exception.BadRequestException;
import com.interview.assessment.notification.persistence.DeliveryRepository;
import com.interview.assessment.notification.persistence.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationIngestionService {

    private final ChannelRouter channelRouter;
    private final NotificationRepository notificationRepository;
    private final DeliveryRepository deliveryRepository;
    private final IdempotencyService idempotencyService;
    private final StatusQueryService statusQueryService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public NotificationIngestionService(ChannelRouter channelRouter,
                                        NotificationRepository notificationRepository,
                                        DeliveryRepository deliveryRepository,
                                        IdempotencyService idempotencyService,
                                        StatusQueryService statusQueryService,
                                        AuditService auditService,
                                        ObjectMapper objectMapper) {
        this.channelRouter = channelRouter;
        this.notificationRepository = notificationRepository;
        this.deliveryRepository = deliveryRepository;
        this.idempotencyService = idempotencyService;
        this.statusQueryService = statusQueryService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public NotificationAcceptResponse submit(SubmitNotificationRequest request, String idempotencyKey) {
        Instant now = Instant.now();
        validateScheduleWindow(request, now);

        String requestHash = idempotencyService.requestHash(writeRequestAsJson(request));
        UUID candidateId = request.notificationId() == null ? UUID.randomUUID() : request.notificationId();
        IdempotencyService.Reservation reservation = idempotencyService.reserveOrReplay(
                request.sourceSystem(),
                idempotencyKey,
                requestHash,
                candidateId,
                now
        );
        if (reservation.replay()) {
            return statusQueryService.getAcceptView(reservation.notificationId(), true);
        }

        UUID notificationId = reservation.notificationId();
        String correlationId = request.correlationId() == null || request.correlationId().isBlank()
                ? request.eventId() : request.correlationId();

        List<com.interview.assessment.notification.domain.enums.Channel> selectedChannels = routeChannels(request);

        notificationRepository.insert(new NotificationRepository.NotificationRow(
                notificationId,
                request.sourceSystem(),
                request.eventId(),
                correlationId,
                request.type(),
                request.severity().name(),
                request.priority().name(),
                NotificationStatus.QUEUED,
                selectedChannels,
                now,
                now
        ));
        notificationRepository.insertRecipients(notificationId, request.recipients(), now);

        for (RecipientDto recipient : request.recipients()) {
            for (var channel : selectedChannels) {
                UUID deliveryId = UUID.randomUUID();
                deliveryRepository.insert(new DeliveryRepository.DeliveryRow(
                        deliveryId,
                        notificationId,
                        recipient.recipientId(),
                        channel,
                        DeliveryStatus.PENDING,
                        0,
                        now,
                        null,
                        null,
                        now,
                        now
                ));
                auditService.append(notificationId, deliveryId, "DELIVERY_QUEUED",
                        "{\"recipientId\":\"" + recipient.recipientId() + "\",\"channel\":\"" + channel.name() + "\"}");
            }
        }

        auditService.append(notificationId, null, "ROUTING_DECISION",
                "{\"selectedChannels\":\"" + selectedChannels + "\"}");
        auditService.append(notificationId, null, "NOTIFICATION_ACCEPTED", "{\"status\":\"QUEUED\"}");

        return new NotificationAcceptResponse(notificationId, NotificationStatus.QUEUED, false, selectedChannels, now);
    }

    private void validateScheduleWindow(SubmitNotificationRequest request, Instant now) {
        if (request.expiresAt() != null && !request.expiresAt().isAfter(now)) {
            throw new BadRequestException("expiresAt must be in the future");
        }
        if (request.scheduleAt() != null && request.expiresAt() != null && !request.expiresAt().isAfter(request.scheduleAt())) {
            throw new BadRequestException("expiresAt must be later than scheduleAt");
        }
    }

    private List<com.interview.assessment.notification.domain.enums.Channel> routeChannels(SubmitNotificationRequest request) {
        List<com.interview.assessment.notification.domain.enums.Channel> selectedChannels = new ArrayList<>();
        for (RecipientDto recipient : request.recipients()) {
            selectedChannels.addAll(channelRouter.route(new RoutingInput(request.requestedChannels(), recipient)).channels());
        }
        return selectedChannels.stream().distinct().toList();
    }

    private String writeRequestAsJson(SubmitNotificationRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize request", e);
        }
    }
}

