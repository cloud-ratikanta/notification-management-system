package com.interview.assessment.notification.integration;

import com.interview.assessment.notification.config.NotificationProperties;
import com.interview.assessment.notification.domain.RetryPolicy;
import com.interview.assessment.notification.domain.enums.Channel;
import com.interview.assessment.notification.domain.enums.DeliveryStatus;
import com.interview.assessment.notification.domain.enums.FailureClass;
import com.interview.assessment.notification.persistence.AuditRepository;
import com.interview.assessment.notification.persistence.DeliveryRepository;
import com.interview.assessment.notification.persistence.NotificationRepository;
import com.interview.assessment.notification.service.DeliveryOrchestrator;
import com.interview.assessment.notification.strategy.ChannelDeliveryStrategy;
import com.interview.assessment.notification.strategy.DeliveryCommand;
import com.interview.assessment.notification.strategy.DeliveryResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("acceptance")
class RetryExhaustedIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public ChannelDeliveryStrategy alwaysFailing() {
            return new ChannelDeliveryStrategy() {
                @Override
                public Channel channel() {
                    return Channel.EMAIL;
                }

                @Override
                public DeliveryResult deliver(DeliveryCommand command) {
                    return DeliveryResult.failed(FailureClass.TRANSIENT_PROVIDER_FAILURE, "always-fail");
                }
            };
        }

        @Bean
        @Primary
        public com.interview.assessment.notification.service.AuditService auditService(com.interview.assessment.notification.persistence.AuditRepository repo) {
            return new com.interview.assessment.notification.service.AuditService(repo);
        }

        @Bean
        @Primary
        public RetryPolicy testRetryPolicy() {
            NotificationProperties props = new NotificationProperties();
            props.getRetry().setMaxAttempts(2);
            props.getRetry().setBaseDelayMs(10L);
            props.getRetry().setMaxDelayMs(100L);
            return new RetryPolicy(props.getRetry(), new java.util.Random(0));
        }
    }

    @Autowired
    DeliveryOrchestrator orchestrator;

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    DeliveryRepository deliveryRepository;

    @Autowired
    AuditRepository auditRepository;

    @Test
    void failing_until_exhaust_emits_retry_scheduled_and_exhausted() {
        UUID notificationId = UUID.randomUUID();
        Instant now = Instant.now();

        notificationRepository.insert(new NotificationRepository.NotificationRow(
                notificationId,
                "svc",
                "evt",
                "corr",
                "type",
                "HIGH",
                "NORMAL",
                com.interview.assessment.notification.domain.enums.NotificationStatus.QUEUED,
                List.of(Channel.EMAIL),
                null,
                null,
                now,
                now
        ));

        notificationRepository.insertRecipients(notificationId,
                List.of(new com.interview.assessment.notification.dto.RecipientDto("r1", "a@b.com", null, null, List.of(), List.of())), now);

        UUID deliveryId = UUID.randomUUID();
        deliveryRepository.insert(new DeliveryRepository.DeliveryRow(
                deliveryId,
                notificationId,
                "r1",
                Channel.EMAIL,
                DeliveryStatus.IN_FLIGHT,
                1,
                null,
                null,
                null,
                "a@b.com",
                null,
                null,
                now,
                now
        ));

        var row = deliveryRepository.findByNotificationId(notificationId).get(0);
        // first attempt -> schedule retry
        orchestrator.process(row);

        // second attempt -> terminal (max attempts) -> RETRY_EXHAUSTED should be emitted
        var second = new DeliveryRepository.DeliveryRow(row.id(), row.notificationId(), row.recipientId(), row.channel(), row.status(), 2, row.nextAttemptAt(), row.lastErrorClass(), row.lastAttemptAt(), row.recipientEmail(), row.recipientPhone(), row.recipientSlackTarget(), row.createdAt(), Instant.now());
        orchestrator.process(second);

        var audits = auditRepository.findByNotificationId(notificationId);
        assertThat(audits).extracting(r -> r.eventType()).contains("RETRY_SCHEDULED", "RETRY_EXHAUSTED");
    }
}

