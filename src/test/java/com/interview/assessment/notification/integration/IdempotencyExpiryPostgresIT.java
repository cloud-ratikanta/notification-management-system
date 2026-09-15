package com.interview.assessment.notification.integration;

import com.interview.assessment.notification.dto.NotificationAcceptResponse;
import com.interview.assessment.notification.dto.RecipientDto;
import com.interview.assessment.notification.dto.SubmitNotificationRequest;
import com.interview.assessment.notification.persistence.IdempotencyRepository;
import com.interview.assessment.notification.persistence.IdempotencyRepository.IdempotencyRecord;
import com.interview.assessment.notification.service.NotificationIngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.DockerClientFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Assumptions;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("acceptance")
class IdempotencyExpiryPostgresIT {

    static PostgreSQLContainer<?> postgres;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        // If container wasn't started (Docker unavailable) these suppliers will not be used because tests are skipped via assumptions
        r.add("spring.datasource.url", () -> postgres == null ? null : postgres.getJdbcUrl());
        r.add("spring.datasource.username", () -> postgres == null ? null : postgres.getUsername());
        r.add("spring.datasource.password", () -> postgres == null ? null : postgres.getPassword());
    }

    @BeforeAll
    static void beforeAll() {
        boolean dockerAvailable = false;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            dockerAvailable = false;
        }
        Assumptions.assumeTrue(dockerAvailable, "Docker not available - skipping Postgres integration tests");

        postgres = new PostgreSQLContainer<>("postgres:15-alpine")
                .withDatabaseName("notification_test")
                .withUsername("test")
                .withPassword("test");
        postgres.start();
    }

    @Autowired
    IdempotencyRepository idempotencyRepository;

    @Autowired
    NotificationIngestionService ingestionService;

    @Test
    void expired_idempotency_allows_new_notification_postgres() {
        String source = "svc-it";
        String key = "idem-it-1";
        UUID oldNotificationId = UUID.randomUUID();
        Instant now = Instant.now();

        // insert an expired idempotency record (expires in past)
        IdempotencyRecord expired = new IdempotencyRecord(source, key, oldNotificationId, "h", now.minusSeconds(7200L), now.minusSeconds(3600L));
        idempotencyRepository.insert(expired);

        // submit a new notification with same idempotency key
        SubmitNotificationRequest req = new SubmitNotificationRequest(
                null,
                source,
                "evt-it",
                null,
                "type-it",
                com.interview.assessment.notification.domain.enums.Severity.LOW,
                com.interview.assessment.notification.domain.enums.Priority.NORMAL,
                "title",
                "body",
                null,
                java.util.Map.of(),
                List.of(new RecipientDto("r1", "a@b.com", null, null, List.of(), List.of())),
                List.of(),
                null,
                null
        );

        NotificationAcceptResponse resp = ingestionService.submit(req, key);
        assertThat(resp).isNotNull();
        assertThat(resp.notificationId()).isNotNull();
        assertThat(resp.duplicate()).isFalse();
    }
}

