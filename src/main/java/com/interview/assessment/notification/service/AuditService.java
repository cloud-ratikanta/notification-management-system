package com.interview.assessment.notification.service;

import com.interview.assessment.notification.dto.AuditEventDto;
import com.interview.assessment.notification.persistence.AuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AuditService {

    private final AuditRepository auditRepository;

    private final AuditSanitizer sanitizer;
    @Autowired
    public AuditService(AuditRepository auditRepository, AuditSanitizer sanitizer) {
        this.auditRepository = auditRepository;
        // ensure we always have a sanitizer even if null is injected (tests or legacy callers)
        this.sanitizer = sanitizer == null ? new AuditSanitizer(new com.fasterxml.jackson.databind.ObjectMapper()) : sanitizer;
    }

    public void append(UUID notificationId, UUID deliveryId, String eventType, String payloadJson) {
        String safe = sanitizer == null ? payloadJson : sanitizer.sanitize(payloadJson);
        auditRepository.append(notificationId, deliveryId, eventType, safe, Instant.now());
    }

    public List<AuditEventDto> getByNotificationId(UUID notificationId) {
        return auditRepository.findByNotificationId(notificationId).stream()
                .map(row -> new AuditEventDto(
                        row.id(),
                        row.notificationId(),
                        row.deliveryId(),
                        row.eventType(),
                        row.payloadJson(),
                        row.createdAt()
                ))
                .toList();
    }
}

