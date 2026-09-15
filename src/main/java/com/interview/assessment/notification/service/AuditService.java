package com.interview.assessment.notification.service;

import com.interview.assessment.notification.persistence.AuditRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuditService {

    private final AuditRepository auditRepository;

    public AuditService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    public void append(UUID notificationId, UUID deliveryId, String eventType, String payloadJson) {
        auditRepository.append(notificationId, deliveryId, eventType, payloadJson, Instant.now());
    }
}

