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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationIngestionService {

    private final ChannelRouter channelRouter;
    private final NotificationRepository notificationRepository;
    private final DeliveryRepository deliveryRepository;
    private final IdempotencyService idempotencyService;
    private final StatusQueryService statusQueryService;
    private final AuditService auditService;
    private final ContentDedupService contentDedupService;
    private final ObjectMapper objectMapper;

    public NotificationIngestionService(ChannelRouter channelRouter,
                                        NotificationRepository notificationRepository,
                                        DeliveryRepository deliveryRepository,
                                        IdempotencyService idempotencyService,
                                        StatusQueryService statusQueryService,
                                        AuditService auditService,
                                        ContentDedupService contentDedupService,
                                        ObjectMapper objectMapper) {
        this.channelRouter = channelRouter;
        this.notificationRepository = notificationRepository;
        this.deliveryRepository = deliveryRepository;
        this.idempotencyService = idempotencyService;
        this.statusQueryService = statusQueryService;
        this.auditService = auditService;
        this.contentDedupService = contentDedupService;
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

        // Content deduplication: suppress logically duplicate content from being created twice
        java.util.Optional<UUID> dup = contentDedupService.findOrRegister(request, reservation.notificationId(), now);
        if (dup.isPresent()) {
            // Found existing notification with same content within dedup window -> record audit and replay view
            UUID canonicalId = dup.get();
            // record that we suppressed creation of candidate notification in favor of canonical
            auditService.append(canonicalId, null, "DUPLICATE_SUPPRESSED",
                    "{\"canonicalNotificationId\":\"" + canonicalId + "\",\"candidateNotificationId\":\"" + reservation.notificationId() + "\",\"mechanism\":\"content_dedup\"}");
            return statusQueryService.getAcceptView(canonicalId, true);
        }

        UUID notificationId = reservation.notificationId();
        String correlationId = request.correlationId() == null || request.correlationId().isBlank()
                ? request.eventId() : request.correlationId();

        Map<RecipientDto, List<com.interview.assessment.notification.domain.enums.Channel>> routingByRecipient = routeChannels(request);
        List<com.interview.assessment.notification.domain.enums.Channel> selectedChannels = routingByRecipient.values().stream()
                .flatMap(List::stream)
                .distinct()
                .toList();

        if (selectedChannels.isEmpty()) {
            throw new BadRequestException("NO_ELIGIBLE_CHANNEL");
        }

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
                request.scheduleAt(),
                request.expiresAt(),
                now,
                now
        ));
        notificationRepository.insertRecipients(notificationId, request.recipients(), now);

        Instant firstAttemptAt = request.scheduleAt() == null ? now : request.scheduleAt();
        for (Map.Entry<RecipientDto, List<com.interview.assessment.notification.domain.enums.Channel>> entry : routingByRecipient.entrySet()) {
            RecipientDto recipient = entry.getKey();
            for (var channel : entry.getValue()) {
                UUID deliveryId = UUID.randomUUID();
                deliveryRepository.insert(new DeliveryRepository.DeliveryRow(
                        deliveryId,
                        notificationId,
                        recipient.recipientId(),
                        channel,
                        DeliveryStatus.PENDING,
                        0,
                        firstAttemptAt,
                        null,
                        null,
                        recipient.email(),
                        recipient.phone(),
                        recipient.slackUserOrChannel(),
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

    private Map<RecipientDto, List<com.interview.assessment.notification.domain.enums.Channel>> routeChannels(SubmitNotificationRequest request) {
        Map<RecipientDto, List<com.interview.assessment.notification.domain.enums.Channel>> selectedChannels = new LinkedHashMap<>();
        for (RecipientDto recipient : request.recipients()) {
            List<com.interview.assessment.notification.domain.enums.Channel> channels = channelRouter
                    .route(new RoutingInput(request.requestedChannels(), request.severity(), recipient))
                    .channels();
            selectedChannels.put(recipient, new ArrayList<>(channels));
        }
        return selectedChannels;
    }

    private String writeRequestAsJson(SubmitNotificationRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize request", e);
        }
    }
}

