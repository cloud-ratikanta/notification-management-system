package com.interview.assessment.notification.service;

import com.interview.assessment.notification.exception.ConflictException;
import com.interview.assessment.notification.persistence.IdempotencyRepository;
import com.interview.assessment.notification.persistence.IdempotencyRepository.IdempotencyRecord;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class IdempotencyService {

    private final IdempotencyRepository idempotencyRepository;
    private final com.interview.assessment.notification.config.NotificationProperties props;

    public IdempotencyService(IdempotencyRepository idempotencyRepository, com.interview.assessment.notification.config.NotificationProperties props) {
        this.idempotencyRepository = idempotencyRepository;
        this.props = props;
    }

    public Reservation reserveOrReplay(String sourceSystem, String key, String requestHash, UUID candidateNotificationId, Instant now) {
        if (key == null || key.isBlank()) {
            return Reservation.newSubmission(candidateNotificationId);
        }

        Optional<IdempotencyRecord> existing = idempotencyRepository.find(sourceSystem, key);
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (record.expiresAt().isAfter(now)) {
                validateHash(record, requestHash);
                return Reservation.replay(record.notificationId());
            }
            idempotencyRepository.delete(sourceSystem, key);
        }

        long ttlSeconds = props == null ? Duration.ofHours(24).toSeconds() : props.getIdempotency().getTtlSeconds();
        IdempotencyRecord toInsert = new IdempotencyRecord(
                sourceSystem,
                key,
                candidateNotificationId,
                requestHash,
                now,
                now.plusSeconds(ttlSeconds)
        );

        try {
            idempotencyRepository.insert(toInsert);
            return Reservation.newSubmission(candidateNotificationId);
        } catch (DuplicateKeyException ignored) {
            // Lost a race to another request with the same key; resolve by read.
            IdempotencyRecord raced = idempotencyRepository.find(sourceSystem, key)
                    .orElseThrow(() -> new IllegalStateException("Idempotency record missing after duplicate key conflict"));
            validateHash(raced, requestHash);
            return Reservation.replay(raced.notificationId());
        }
    }

    private void validateHash(IdempotencyRecord existing, String requestHash) {
        if (!existing.requestHash().equals(requestHash)) {
            throw new ConflictException("Idempotency key reused with a different payload");
        }
    }

    public String requestHash(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Missing SHA-256 support", e);
        }
    }

    public record Reservation(UUID notificationId, boolean replay) {
        static Reservation newSubmission(UUID notificationId) {
            return new Reservation(notificationId, false);
        }

        static Reservation replay(UUID notificationId) {
            return new Reservation(notificationId, true);
        }
    }
}

