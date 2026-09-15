package com.interview.assessment.notification;

import com.interview.assessment.notification.domain.enums.Channel;
import com.interview.assessment.notification.domain.enums.DeliveryStatus;
import com.interview.assessment.notification.domain.enums.FailureClass;
import com.interview.assessment.notification.persistence.DeliveryRepository;
import com.interview.assessment.notification.service.DeliveryOrchestrator;
import com.interview.assessment.notification.service.AuditService;
import com.interview.assessment.notification.strategy.ChannelDeliveryStrategy;
import com.interview.assessment.notification.strategy.ChannelStrategyRegistry;
import com.interview.assessment.notification.strategy.DeliveryCommand;
import com.interview.assessment.notification.strategy.DeliveryResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

class DeliveryOrchestratorRetryUnitTest {

    // stub strategy that alternates: first call fails, second succeeds
    static class FlakyStrategy implements ChannelDeliveryStrategy {
        private int calls = 0;

        @Override
        public Channel channel() { return Channel.EMAIL; }

        @Override
        public DeliveryResult deliver(DeliveryCommand command) {
            calls++;
            if (calls == 1) {
                return DeliveryResult.failed(FailureClass.TRANSIENT_PROVIDER_FAILURE, "temporary");
            }
            return DeliveryResult.ok();
        }
    }

    @Test
    void transientFailure_then_retryScheduled_then_succeeds() {
        // mocks
        DeliveryRepository repo = mock(DeliveryRepository.class);
        AuditService audit = mock(AuditService.class);

        // retry policy with maxAttempts=3
        com.interview.assessment.notification.config.NotificationProperties props = new com.interview.assessment.notification.config.NotificationProperties();
        props.getRetry().setMaxAttempts(3);
        com.interview.assessment.notification.domain.RetryPolicy retryPolicy = new com.interview.assessment.notification.domain.RetryPolicy(props.getRetry(), new java.util.Random(1L));

        // orchestrator with flaky strategy
        FlakyStrategy flaky = new FlakyStrategy();
        ChannelStrategyRegistry registry = new ChannelStrategyRegistry(List.of(flaky));
        DeliveryOrchestrator orchestrator = new DeliveryOrchestrator(registry, repo, audit, retryPolicy);

        UUID deliveryId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        // first attempt (attemptCount = 0)
        DeliveryRepository.DeliveryRow row1 = new DeliveryRepository.DeliveryRow(
                deliveryId, notificationId, "r1", Channel.EMAIL, DeliveryStatus.PENDING, 0,
                Instant.now(), null, null, "a@b.com", null, null, Instant.now(), Instant.now()
        );

        orchestrator.process(row1);

        // verify retry scheduled called once and audit RETRY_SCHEDULED emitted
        verify(repo, times(1)).scheduleRetry(eq(deliveryId), any(Instant.class), any(Instant.class));
        verify(audit, atLeastOnce()).append(eq(notificationId), eq(deliveryId), eq("RETRY_SCHEDULED"), anyString());

        // simulate second attempt where attemptCount = 1 and strategy now succeeds
        DeliveryRepository.DeliveryRow row2 = new DeliveryRepository.DeliveryRow(
                deliveryId, notificationId, "r1", Channel.EMAIL, DeliveryStatus.RETRY_SCHEDULED, 1,
                Instant.now(), null, null, "a@b.com", null, null, Instant.now(), Instant.now()
        );

        orchestrator.process(row2);

        verify(repo, times(1)).markSucceeded(eq(deliveryId), any(Instant.class));
        verify(audit, atLeastOnce()).append(eq(notificationId), eq(deliveryId), eq("DELIVERY_SUCCEEDED"), anyString());
    }

    @Test
    void retry_exhausted_emits_retry_exhausted_audit() {
        DeliveryRepository repo = mock(DeliveryRepository.class);
        AuditService audit = mock(AuditService.class);

        com.interview.assessment.notification.config.NotificationProperties props = new com.interview.assessment.notification.config.NotificationProperties();
        props.getRetry().setMaxAttempts(1); // no retries allowed beyond attemptCount 1
        com.interview.assessment.notification.domain.RetryPolicy retryPolicy = new com.interview.assessment.notification.domain.RetryPolicy(props.getRetry(), new java.util.Random(1L));

        // strategy always fails
        ChannelDeliveryStrategy failing = new ChannelDeliveryStrategy() {
            @Override public Channel channel() { return Channel.EMAIL; }
            @Override public DeliveryResult deliver(DeliveryCommand command) {
                return DeliveryResult.failed(FailureClass.TRANSIENT_PROVIDER_FAILURE, "boom");
            }
        };

        ChannelStrategyRegistry registry = new ChannelStrategyRegistry(List.of(failing));
        DeliveryOrchestrator orchestrator = new DeliveryOrchestrator(registry, repo, audit, retryPolicy);

        UUID deliveryId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();

        // simulate attemptCount == maxAttempts (1) => should be terminal and emit RETRY_EXHAUSTED
        DeliveryRepository.DeliveryRow row = new DeliveryRepository.DeliveryRow(
                deliveryId, notificationId, "r1", Channel.EMAIL, DeliveryStatus.PENDING, 1,
                Instant.now(), null, null, "a@b.com", null, null, Instant.now(), Instant.now()
        );

        orchestrator.process(row);

        verify(repo, times(1)).markFailed(eq(deliveryId), any(FailureClass.class), any(Instant.class));
        verify(audit, atLeastOnce()).append(eq(notificationId), eq(deliveryId), eq("RETRY_EXHAUSTED"), anyString());
    }
}

