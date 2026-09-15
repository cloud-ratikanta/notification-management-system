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
    private final com.interview.assessment.notification.domain.RetryPolicy retryPolicy;

    public DeliveryOrchestrator(ChannelStrategyRegistry registry,
                                DeliveryRepository deliveryRepository,
                                AuditService auditService,
                                com.interview.assessment.notification.domain.RetryPolicy retryPolicy) {
        this.registry = registry;
        this.deliveryRepository = deliveryRepository;
        this.auditService = auditService;
        this.retryPolicy = retryPolicy;
    }

    public void process(DeliveryRepository.DeliveryRow row) {
        Instant startedAt = Instant.now();
        auditService.append(row.notificationId(), row.id(), "DELIVERY_ATTEMPTED", "{\"attemptNo\":" + row.attemptCount() + ",\"channel\":\"" + row.channel() + "\"}");

        DeliveryResult result = registry.get(row.channel()).deliver(new DeliveryCommand(
                row.id(),
                row.notificationId(),
                row.recipientId(),
                row.recipientEmail(),
                row.recipientPhone(),
                row.recipientSlackTarget(),
                row.channel(),
                row.attemptCount()
        ));

        Instant finishedAt = Instant.now();
        if (result.success()) {
            deliveryRepository.markSucceeded(row.id(), finishedAt);
            deliveryRepository.recordAttempt(row.id(), row.attemptCount(), "SUCCEEDED", null, result.safeDetail(), startedAt, finishedAt);
            auditService.append(row.notificationId(), row.id(), "DELIVERY_SUCCEEDED", "{\"channel\":\"" + row.channel() + "\"}");
        } else {
            FailureClass failureClass = result.failureClass() == null ? FailureClass.UNKNOWN : result.failureClass();

            // Decide whether to schedule a retry or mark terminal failure
            if (retryPolicy != null && retryPolicy.isRetryable(failureClass) && row.attemptCount() < retryPolicy.getMaxAttempts()) {
                // compute next attempt using next attempt number (currentAttempt+1)
                long delayMs = retryPolicy.nextDelayMs(row.attemptCount() + 1, failureClass,
                        result.retryAfterSeconds() == null ? java.util.Optional.empty() : java.util.Optional.of(result.retryAfterSeconds()));
                java.time.Instant nextAttemptAt = finishedAt.plusMillis(delayMs);
                deliveryRepository.scheduleRetry(row.id(), nextAttemptAt, finishedAt);
                deliveryRepository.recordAttempt(row.id(), row.attemptCount(), "FAILED", failureClass, result.safeDetail(), startedAt, finishedAt);
                auditService.append(row.notificationId(), row.id(), "RETRY_SCHEDULED",
                        "{\"nextAttemptAt\":\"" + nextAttemptAt + "\",\"failureClass\":\"" + failureClass + "\"}");
            } else {
                // terminal failure
                deliveryRepository.markFailed(row.id(), failureClass, finishedAt);
                deliveryRepository.recordAttempt(row.id(), row.attemptCount(), "FAILED", failureClass, result.safeDetail(), startedAt, finishedAt);
                auditService.append(row.notificationId(), row.id(), "DELIVERY_FAILED", "{\"failureClass\":\"" + failureClass + "\"}");
                if (retryPolicy != null && !retryPolicy.isRetryable(failureClass)) {
                    auditService.append(row.notificationId(), row.id(), "RETRY_EXHAUSTED", "{\"reason\":\"non_retryable\"}");
                } else if (retryPolicy != null && row.attemptCount() >= retryPolicy.getMaxAttempts()) {
                    auditService.append(row.notificationId(), row.id(), "RETRY_EXHAUSTED", "{\"reason\":\"max_attempts\"}");
                }
            }
        }
    }
}

