package com.interview.assessment.notification.service;

import com.interview.assessment.notification.domain.enums.FailureClass;
import com.interview.assessment.notification.persistence.DeliveryRepository;
import com.interview.assessment.notification.strategy.ChannelStrategyRegistry;
import com.interview.assessment.notification.strategy.DeliveryCommand;
import com.interview.assessment.notification.strategy.DeliveryResult;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class DeliveryOrchestrator {

    private final ChannelStrategyRegistry registry;
    private final DeliveryRepository deliveryRepository;
    private final AuditService auditService;

    public DeliveryOrchestrator(ChannelStrategyRegistry registry,
                                DeliveryRepository deliveryRepository,
                                AuditService auditService) {
        this.registry = registry;
        this.deliveryRepository = deliveryRepository;
        this.auditService = auditService;
    }

    public void process(DeliveryRepository.DeliveryRow row) {
        DeliveryResult result = registry.get(row.channel()).deliver(new DeliveryCommand(
                row.id(),
                row.notificationId(),
                row.recipientId(),
                row.channel(),
                row.attemptCount()
        ));

        if (result.success()) {
            deliveryRepository.markSucceeded(row.id(), Instant.now());
            auditService.append(row.notificationId(), row.id(), "DELIVERY_SUCCEEDED", "{\"channel\":\"" + row.channel() + "\"}");
        } else {
            FailureClass failureClass = result.failureClass() == null ? FailureClass.UNKNOWN : result.failureClass();
            deliveryRepository.markFailed(row.id(), failureClass, Instant.now());
            auditService.append(row.notificationId(), row.id(), "DELIVERY_FAILED", "{\"failureClass\":\"" + failureClass + "\"}");
        }
    }
}

