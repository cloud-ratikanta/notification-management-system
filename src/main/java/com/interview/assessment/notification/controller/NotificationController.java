package com.interview.assessment.notification.controller;

import com.interview.assessment.notification.dto.AuditEventDto;
import com.interview.assessment.notification.dto.NotificationAcceptResponse;
import com.interview.assessment.notification.dto.NotificationStatusResponse;
import com.interview.assessment.notification.dto.SubmitNotificationRequest;
import com.interview.assessment.notification.service.AuditService;
import com.interview.assessment.notification.service.NotificationIngestionService;
import com.interview.assessment.notification.service.StatusQueryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationIngestionService ingestionService;
    private final StatusQueryService statusQueryService;
    private final AuditService auditService;

    public NotificationController(NotificationIngestionService ingestionService,
                                  StatusQueryService statusQueryService,
                                  AuditService auditService) {
        this.ingestionService = ingestionService;
        this.statusQueryService = statusQueryService;
        this.auditService = auditService;
    }

    @PostMapping
    public ResponseEntity<NotificationAcceptResponse> submit(
            @Valid @RequestBody SubmitNotificationRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        NotificationAcceptResponse response = ingestionService.submit(request, idempotencyKey);
        if (response.duplicate()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/notifications/" + response.notificationId()))
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationStatusResponse> getStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(statusQueryService.getStatus(id));
    }

    @GetMapping("/{id}/audit")
    public ResponseEntity<List<AuditEventDto>> getAudit(@PathVariable UUID id) {
        statusQueryService.getStatus(id);
        return ResponseEntity.ok(auditService.getByNotificationId(id));
    }
}

