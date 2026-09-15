package com.interview.assessment.notification.worker;

import com.interview.assessment.notification.config.NotificationProperties;
import com.interview.assessment.notification.persistence.DeliveryRepository;
import com.interview.assessment.notification.persistence.NotificationRepository;
import com.interview.assessment.notification.service.AuditService;
import com.interview.assessment.notification.service.DeliveryOrchestrator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(name = "notification.worker.enabled", havingValue = "true", matchIfMissing = true)
public class DeliveryWorker {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryOrchestrator deliveryOrchestrator;
    private final NotificationRepository notificationRepository;
    private final AuditService auditService;
    private final NotificationProperties properties;

    public DeliveryWorker(DeliveryRepository deliveryRepository,
                          DeliveryOrchestrator deliveryOrchestrator,
                          NotificationRepository notificationRepository,
                          AuditService auditService,
                          NotificationProperties properties) {
        this.deliveryRepository = deliveryRepository;
        this.deliveryOrchestrator = deliveryOrchestrator;
        this.notificationRepository = notificationRepository;
        this.auditService = auditService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${notification.worker.fixed-delay-ms:1000}")
    public void tick() {
        Instant now = Instant.now();
        List<DeliveryRepository.DeliveryRow> claimed = deliveryRepository.claimQueuedBatch(
                properties.getWorker().getBatchSize(),
                now
        );

        for (DeliveryRepository.DeliveryRow row : claimed) {
            boolean expired = notificationRepository.findById(row.notificationId())
                    .map(notification -> notification.expiresAt() != null && !notification.expiresAt().isAfter(now))
                    .orElse(false);

            if (expired) {
                deliveryRepository.markExpired(row.id(), now);
                auditService.append(row.notificationId(), row.id(), "DELIVERY_FAILED", "{\"failureClass\":\"EXPIRED\"}");
                continue;
            }

            deliveryOrchestrator.process(row);
        }
    }
}
