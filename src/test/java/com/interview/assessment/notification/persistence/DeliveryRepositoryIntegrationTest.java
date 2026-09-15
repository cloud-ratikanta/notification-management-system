package com.interview.assessment.notification.persistence;

import com.interview.assessment.notification.domain.enums.DeliveryStatus;
import com.interview.assessment.notification.persistence.DeliveryRepository.DeliveryRow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("acceptance")
@AutoConfigureTestDatabase
class DeliveryRepositoryIntegrationTest {

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void recordAttempt_uniquenessEnforced() {
        UUID notifId = UUID.randomUUID();
        notificationRepository.insert(new NotificationRepository.NotificationRow(
                notifId, "src", "evt", "corr", "T", "HIGH", "NORMAL",
                com.interview.assessment.notification.domain.enums.NotificationStatus.QUEUED,
                List.of(), null, null, Instant.now(), Instant.now()
        ));

        UUID deliveryId = UUID.randomUUID();
        deliveryRepository.insert(new DeliveryRepository.DeliveryRow(
                deliveryId,
                notifId,
                "r1",
                com.interview.assessment.notification.domain.enums.Channel.EMAIL,
                DeliveryStatus.PENDING,
                0,
                Instant.now(),
                null,
                null,
                "a@b.com",
                null,
                null,
                Instant.now(),
                Instant.now()
        ));

        // first attempt insertion should succeed
        deliveryRepository.recordAttempt(deliveryId, 1, "FAILED", null, "x", Instant.now(), Instant.now());

        // second insertion with same (delivery_id, attempt_no) should fail due to unique constraint
        assertThrows(DataAccessException.class, () ->
                deliveryRepository.recordAttempt(deliveryId, 1, "FAILED", null, "x", Instant.now(), Instant.now())
        );
    }

    @Test
    void claimQueuedBatch_onlyClaimsPendingOrRetry() {
        UUID notifId = UUID.randomUUID();
        notificationRepository.insert(new NotificationRepository.NotificationRow(
                notifId, "src", "evt2", "corr2", "T", "HIGH", "NORMAL",
                com.interview.assessment.notification.domain.enums.NotificationStatus.QUEUED,
                List.of(), null, null, Instant.now(), Instant.now()
        ));

        UUID pendingId = UUID.randomUUID();
        deliveryRepository.insert(new DeliveryRepository.DeliveryRow(
                pendingId,
                notifId,
                "r2",
                com.interview.assessment.notification.domain.enums.Channel.EMAIL,
                DeliveryStatus.PENDING,
                0,
                Instant.now(),
                null,
                null,
                "a@b.com",
                null,
                null,
                Instant.now(),
                Instant.now()
        ));

        UUID inflightId = UUID.randomUUID();
        deliveryRepository.insert(new DeliveryRepository.DeliveryRow(
                inflightId,
                notifId,
                "r3",
                com.interview.assessment.notification.domain.enums.Channel.EMAIL,
                DeliveryStatus.IN_FLIGHT,
                1,
                Instant.now(),
                null,
                null,
                "a@b.com",
                null,
                null,
                Instant.now(),
                Instant.now()
        ));

        List<DeliveryRow> claimed = deliveryRepository.claimQueuedBatch(10, Instant.now());
        // only the pending row should be claimed
        assertThat(claimed).extracting(DeliveryRow::id).contains(pendingId);
        assertThat(claimed).extracting(DeliveryRow::id).doesNotContain(inflightId);
    }
}

