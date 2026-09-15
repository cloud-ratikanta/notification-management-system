package com.interview.assessment.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.assessment.notification.dto.RecipientDto;
import com.interview.assessment.notification.dto.SubmitNotificationRequest;
import com.interview.assessment.notification.persistence.ContentDedupRepository;
import com.interview.assessment.notification.config.NotificationProperties;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ContentDedupService {

    private final ContentDedupRepository repository;
    private final ObjectMapper objectMapper;
    private final NotificationProperties props;
    private final Duration window;

    @Autowired
    public ContentDedupService(ContentDedupRepository repository, ObjectMapper objectMapper, NotificationProperties props) {
        this(repository, objectMapper, props, Duration.ofSeconds(props.getDedup().getWindowSeconds()));
    }

    // package-private constructor for tests
    ContentDedupService(ContentDedupRepository repository, ObjectMapper objectMapper, NotificationProperties props, Duration window) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.props = props;
        this.window = window;
    }

    /**
     * Compute canonical content hash for the given request.
     */
    public String contentHash(SubmitNotificationRequest request) {
        try {
            String normalizedRecipients = request.recipients().stream()
                    .sorted((a, b) -> a.recipientId().compareTo(b.recipientId()))
                    .map(r -> r.recipientId() + ":" + safe(r.email()) + ":" + safe(r.phone()) + ":" + safe(r.slackUserOrChannel()))
                    .collect(Collectors.joining("|"));

            String channels = request.requestedChannels() == null ? "" : request.requestedChannels().stream()
                    .map(Enum::name).sorted().collect(Collectors.joining(","));

            // Build canonical key based on configured fields
            StringBuilder sb = new StringBuilder();

            // always include sourceSystem
            sb.append(safe(request.sourceSystem())).append("|");

            if (props != null && props.getDedup().isIncludeEventId()) {
                sb.append(safe(request.eventId()));
            }
            sb.append("|");

            sb.append(safe(request.type())).append("|");
            sb.append(safe(request.severity() == null ? "" : request.severity().name())).append("|");

            // template key and params
            if (props == null || props.getDedup().isIncludeTemplateParams()) {
                sb.append(safe(request.templateKey())).append("|");
                sb.append(sortedMapString(request.templateParams())).append("|");
            } else {
                sb.append("|").append("|");
            }

            if (props == null || props.getDedup().isIncludeTitle()) {
                sb.append(safe(request.title()));
            }
            sb.append("|");

            if (props == null || props.getDedup().isIncludeBody()) {
                sb.append(safe(request.body()));
            }
            sb.append("|");

            sb.append(normalizedRecipients).append("|").append(channels);

            String canonical = sb.toString();

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String safe(Object o) {
        return o == null ? "" : o.toString();
    }

    private String sortedMapString(Map<String, String> map) {
        if (map == null || map.isEmpty()) return "";
        return map.entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + safe(e.getValue()))
                .collect(Collectors.joining("|"));
    }

    /**
     * If there is a recent notification with the same content hash within the dedup window, return its id.
     * Otherwise insert a dedup record mapping this hash to candidateNotificationId and return empty.
     */
    public java.util.Optional<UUID> findOrRegister(SubmitNotificationRequest request, UUID candidateNotificationId, Instant now) {
        String hash = contentHash(request);
        Instant since = now.minus(window);
        var existing = repository.findRecentByHash(hash, since);
        if (existing.isPresent()) {
            return java.util.Optional.of(existing.get().notificationId());
        }

        ContentDedupRepository.ContentDedupRecord record = new ContentDedupRepository.ContentDedupRecord(
                UUID.randomUUID(), hash, candidateNotificationId, now, now.plus(window)
        );
        repository.insert(record);
        return java.util.Optional.empty();
    }
}

