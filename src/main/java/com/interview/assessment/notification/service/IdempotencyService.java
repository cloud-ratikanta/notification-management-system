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

    private static final Duration TTL = Duration.ofHours(24);
    private final IdempotencyRepository idempotencyRepository;

    public IdempotencyService(IdempotencyRepository idempotencyRepository) {
        this.idempotencyRepository = idempotencyRepository;
    }

    public Optional<UUID> findExisting(String sourceSystem, String key, String requestHash) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        Optional<IdempotencyRecord> existing = idempotencyRepository.find(sourceSystem, key);
        if (existing.isEmpty() || existing.get().expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        if (!existing.get().requestHash().equals(requestHash)) {
            throw new ConflictException("Idempotency key reused with a different payload");
        }
        return Optional.of(existing.get().notificationId());
    }

    public void save(String sourceSystem, String key, UUID notificationId, String requestHash) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            idempotencyRepository.insert(new IdempotencyRecord(
                    sourceSystem,
                    key,
                    notificationId,
                    requestHash,
                    Instant.now(),
                    Instant.now().plus(TTL)
            ));
        } catch (DuplicateKeyException ignored) {
            // Another request won the race; caller resolves by read path.
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
}

