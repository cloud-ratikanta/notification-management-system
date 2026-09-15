package com.interview.assessment.notification;

import com.interview.assessment.notification.persistence.IdempotencyRepository;
import com.interview.assessment.notification.service.IdempotencyService;
import com.interview.assessment.notification.persistence.IdempotencyRepository.IdempotencyRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyExpiryTest {

    @Test
    void expired_idempotency_allows_new_submission() {
        IdempotencyRepository repo = mock(IdempotencyRepository.class);
        com.interview.assessment.notification.config.NotificationProperties props = new com.interview.assessment.notification.config.NotificationProperties();
        props.getIdempotency().setTtlSeconds(3600L);
        IdempotencyService service = new IdempotencyService(repo, props);

        String source = "svc";
        String key = "k-expired";
        UUID candidate = UUID.randomUUID();
        Instant now = Instant.now();

        // existing record expired in the past
        IdempotencyRecord expired = new IdempotencyRecord(source, key, UUID.randomUUID(), "h", now.minusSeconds(7200L), now.minusSeconds(3600L));
        when(repo.find(source, key)).thenReturn(Optional.of(expired));

        // expect delete called and then insert called (we let insert be a no-op)
        doNothing().when(repo).delete(source, key);
        doNothing().when(repo).insert(any());

        IdempotencyService.Reservation res = service.reserveOrReplay(source, key, "h2", candidate, now);
        assertThat(res.replay()).isFalse();
        verify(repo, times(1)).delete(source, key);
        verify(repo, times(1)).insert(any());
    }
}

