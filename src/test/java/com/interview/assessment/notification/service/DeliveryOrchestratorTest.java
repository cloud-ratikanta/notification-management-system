package com.interview.assessment.notification.service;

import com.interview.assessment.notification.domain.enums.Channel;
import com.interview.assessment.notification.domain.enums.FailureClass;
import com.interview.assessment.notification.persistence.DeliveryRepository;
import com.interview.assessment.notification.strategy.ChannelStrategyRegistry;
import com.interview.assessment.notification.strategy.EmailDeliveryStrategy;
import com.interview.assessment.notification.strategy.SmsDeliveryStrategy;
import com.interview.assessment.notification.strategy.SlackDeliveryStrategy;
import com.interview.assessment.notification.strategy.DeliveryCommand;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryOrchestratorTest {

    static class FakeDeliveryRepository extends DeliveryRepository {
        List<String> actions = new ArrayList<>();

        public FakeDeliveryRepository() {
            super(null);
        }

        @Override
        public void markSucceeded(UUID deliveryId, Instant now) {
            actions.add("succeeded:" + deliveryId);
        }

        @Override
        public void markFailed(UUID deliveryId, FailureClass failureClass, Instant now) {
            actions.add("failed:" + deliveryId + ":" + failureClass);
        }

        @Override
        public void recordAttempt(UUID deliveryId, int attemptNo, String outcome, FailureClass failureClass, String safeDetail, Instant startedAt, Instant finishedAt) {
            actions.add("attempt:" + deliveryId + ":" + outcome);
        }
    }

    static class FakeAuditService extends AuditService {
        List<String> events = new ArrayList<>();

        public FakeAuditService() {
            super(null);
        }

        @Override
        public void append(UUID notificationId, UUID deliveryId, String eventType, String payloadJson) {
            events.add(eventType + ":" + deliveryId);
        }
    }

    @Test
    void orchestrator_delegates_to_strategy_and_records_success() {
        ChannelStrategyRegistry registry = new ChannelStrategyRegistry(List.of(new EmailDeliveryStrategy(), new SmsDeliveryStrategy(), new SlackDeliveryStrategy()));

        FakeDeliveryRepository repo = new FakeDeliveryRepository();
        FakeAuditService audit = new FakeAuditService();

        DeliveryOrchestrator orchestrator = new DeliveryOrchestrator(registry, repo, audit);

        UUID deliveryId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();

        DeliveryRepository.DeliveryRow row = new DeliveryRepository.DeliveryRow(
                deliveryId,
                notificationId,
                "r1",
                Channel.EMAIL,
                null,
                1,
                Instant.now(),
                null,
                null,
                "a@b.com",
                null,
                null,
                Instant.now(),
                Instant.now()
        );

        orchestrator.process(row);

        assertThat(repo.actions).anyMatch(s -> s.startsWith("succeeded:" + deliveryId));
        assertThat(repo.actions).anyMatch(s -> s.startsWith("attempt:" + deliveryId + ":SUCCEEDED"));
        assertThat(audit.events).contains("DELIVERY_ATTEMPTED:" + deliveryId, "DELIVERY_SUCCEEDED:" + deliveryId);
    }
}

