package com.interview.assessment.notification.worker;

import com.interview.assessment.notification.persistence.DeliveryRepository;
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

    public DeliveryWorker(DeliveryRepository deliveryRepository,
                          DeliveryOrchestrator deliveryOrchestrator) {
        this.deliveryRepository = deliveryRepository;
        this.deliveryOrchestrator = deliveryOrchestrator;
    }

    @Scheduled(fixedDelayString = "${notification.worker.fixed-delay-ms:1000}")
    public void tick() {
        List<DeliveryRepository.DeliveryRow> claimed = deliveryRepository.claimQueuedBatch(
                20,
                Instant.now()
        );
        claimed.forEach(deliveryOrchestrator::process);
    }
}

