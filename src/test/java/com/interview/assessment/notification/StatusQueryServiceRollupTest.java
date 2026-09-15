package com.interview.assessment.notification;

import com.interview.assessment.notification.domain.enums.DeliveryStatus;
import com.interview.assessment.notification.domain.enums.NotificationStatus;
import com.interview.assessment.notification.persistence.DeliveryRepository;
import com.interview.assessment.notification.persistence.NotificationRepository;
import com.interview.assessment.notification.service.StatusQueryService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatusQueryServiceRollupTest {

    @Test
    void partialSuccess_is_completed_with_partialTrue() {
        NotificationRepository repo = mock(NotificationRepository.class);
        DeliveryRepository dRepo = mock(DeliveryRepository.class);
        StatusQueryService s = new StatusQueryService(repo, dRepo);

        UUID id = UUID.randomUUID();
        NotificationRepository.NotificationRow n = new NotificationRepository.NotificationRow(
                id, "src", "evt", "corr", "type", "sev", "pri", NotificationStatus.ACCEPTED, List.of(), null, null, Instant.now(), Instant.now()
        );
        when(repo.findById(id)).thenReturn(java.util.Optional.of(n));

        DeliveryRepository.DeliveryRow a = new DeliveryRepository.DeliveryRow(UUID.randomUUID(), id, "r1", com.interview.assessment.notification.domain.enums.Channel.EMAIL, DeliveryStatus.SUCCEEDED, 1, null, null, Instant.now(), null, null, null, Instant.now(), Instant.now());
        DeliveryRepository.DeliveryRow b = new DeliveryRepository.DeliveryRow(UUID.randomUUID(), id, "r2", com.interview.assessment.notification.domain.enums.Channel.EMAIL, DeliveryStatus.FAILED_TERMINAL, 3, null, null, Instant.now(), null, null, null, Instant.now(), Instant.now());
        when(dRepo.findByNotificationId(id)).thenReturn(List.of(a, b));

        var resp = s.getStatus(id);
        assertThat(resp.status()).isEqualTo(NotificationStatus.COMPLETED);
        assertThat(resp.partialSuccess()).isTrue();
    }

    @Test
    void allFailed_is_failed() {
        NotificationRepository repo = mock(NotificationRepository.class);
        DeliveryRepository dRepo = mock(DeliveryRepository.class);
        StatusQueryService s = new StatusQueryService(repo, dRepo);

        UUID id = UUID.randomUUID();
        NotificationRepository.NotificationRow n = new NotificationRepository.NotificationRow(
                id, "src", "evt", "corr", "type", "sev", "pri", NotificationStatus.ACCEPTED, List.of(), null, null, Instant.now(), Instant.now()
        );
        when(repo.findById(id)).thenReturn(java.util.Optional.of(n));

        DeliveryRepository.DeliveryRow a = new DeliveryRepository.DeliveryRow(UUID.randomUUID(), id, "r1", com.interview.assessment.notification.domain.enums.Channel.EMAIL, DeliveryStatus.FAILED_TERMINAL, 3, null, null, Instant.now(), null, null, null, Instant.now(), Instant.now());
        DeliveryRepository.DeliveryRow b = new DeliveryRepository.DeliveryRow(UUID.randomUUID(), id, "r2", com.interview.assessment.notification.domain.enums.Channel.EMAIL, DeliveryStatus.EXPIRED, 0, null, null, Instant.now(), null, null, null, Instant.now(), Instant.now());
        when(dRepo.findByNotificationId(id)).thenReturn(List.of(a, b));

        var resp = s.getStatus(id);
        assertThat(resp.status()).isEqualTo(NotificationStatus.FAILED);
        assertThat(resp.partialSuccess()).isFalse();
    }

    @Test
    void inProgress_when_any_open() {
        NotificationRepository repo = mock(NotificationRepository.class);
        DeliveryRepository dRepo = mock(DeliveryRepository.class);
        StatusQueryService s = new StatusQueryService(repo, dRepo);

        UUID id = UUID.randomUUID();
        NotificationRepository.NotificationRow n = new NotificationRepository.NotificationRow(
                id, "src", "evt", "corr", "type", "sev", "pri", NotificationStatus.ACCEPTED, List.of(), null, null, Instant.now(), Instant.now()
        );
        when(repo.findById(id)).thenReturn(java.util.Optional.of(n));

        DeliveryRepository.DeliveryRow a = new DeliveryRepository.DeliveryRow(UUID.randomUUID(), id, "r1", com.interview.assessment.notification.domain.enums.Channel.EMAIL, DeliveryStatus.RETRY_SCHEDULED, 1, Instant.now(), null, Instant.now(), null, null, null, Instant.now(), Instant.now());
        when(dRepo.findByNotificationId(id)).thenReturn(List.of(a));

        var resp = s.getStatus(id);
        assertThat(resp.status()).isEqualTo(NotificationStatus.IN_PROGRESS);
    }

    @Test
    void expired_when_window_passed_and_no_success() {
        NotificationRepository repo = mock(NotificationRepository.class);
        DeliveryRepository dRepo = mock(DeliveryRepository.class);
        StatusQueryService s = new StatusQueryService(repo, dRepo);

        UUID id = UUID.randomUUID();
        Instant past = Instant.now().minusSeconds(3600);
        NotificationRepository.NotificationRow n = new NotificationRepository.NotificationRow(
                id, "src", "evt", "corr", "type", "sev", "pri", NotificationStatus.ACCEPTED, List.of(), null, past, Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600)
        );
        when(repo.findById(id)).thenReturn(java.util.Optional.of(n));

        DeliveryRepository.DeliveryRow a = new DeliveryRepository.DeliveryRow(UUID.randomUUID(), id, "r1", com.interview.assessment.notification.domain.enums.Channel.EMAIL, DeliveryStatus.FAILED_TERMINAL, 3, null, null, Instant.now().minusSeconds(4000), null, null, null, Instant.now(), Instant.now());
        when(dRepo.findByNotificationId(id)).thenReturn(List.of(a));

        var resp = s.getStatus(id);
        assertThat(resp.status()).isEqualTo(NotificationStatus.EXPIRED);
    }
}

