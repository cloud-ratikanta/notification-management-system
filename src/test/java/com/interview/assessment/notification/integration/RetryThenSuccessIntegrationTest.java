package com.interview.assessment.notification.integration;

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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("acceptance")
class RetryThenSuccessIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public ChannelDeliveryStrategy emailStub() {
            return new ChannelDeliveryStrategy() {
                private final AtomicInteger calls = new AtomicInteger(0);

                @Override
                public Channel channel() {
                    return Channel.EMAIL;
                }

                @Override
                public DeliveryResult deliver(DeliveryCommand command) {
                    int c = calls.incrementAndGet();
                    if (c == 1) {
                        return DeliveryResult.failed(FailureClass.TRANSIENT_PROVIDER_FAILURE, "simulated-transient");
                    }
                    return DeliveryResult.ok();
                }
            };
        }
        @Bean
        @Primary
        public com.interview.assessment.notification.service.AuditService auditService(com.interview.assessment.notification.persistence.AuditRepository repo) {
            return new com.interview.assessment.notification.service.AuditService(repo);
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
    void transientFailure_then_success_emits_retry_and_success_audits() {
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

        // fetch row and process (first attempt -> transient failure -> retry scheduled)
        var rows = deliveryRepository.findByNotificationId(notificationId);
        assertThat(rows).isNotEmpty();
        var row = rows.get(0);

        orchestrator.process(row);

        var auditsAfterFirst = auditRepository.findByNotificationId(notificationId);
        assertThat(auditsAfterFirst).extracting(r -> r.eventType()).contains("RETRY_SCHEDULED");

        // simulate second attempt (attemptCount=2)
        var secondAttemptRow = new DeliveryRepository.DeliveryRow(
                row.id(), row.notificationId(), row.recipientId(), row.channel(), row.status(), 2,
                row.nextAttemptAt(), row.lastErrorClass(), row.lastAttemptAt(), row.recipientEmail(), row.recipientPhone(), row.recipientSlackTarget(), row.createdAt(), Instant.now()
        );
        orchestrator.process(secondAttemptRow);

        var audits = auditRepository.findByNotificationId(notificationId);
        assertThat(audits).extracting(r -> r.eventType()).contains("DELIVERY_SUCCEEDED");
    }
}

