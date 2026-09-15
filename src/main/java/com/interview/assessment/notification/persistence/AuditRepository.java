package com.interview.assessment.notification.persistence;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public class AuditRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AuditRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void append(UUID notificationId, UUID deliveryId, String eventType, String payloadJson, Instant createdAt) {
        String sql = """
                INSERT INTO audit_event (id, notification_id, delivery_id, event_type, payload_json, created_at)
                VALUES (:id, :notificationId, :deliveryId, :eventType, :payloadJson, :createdAt)
                """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("notificationId", notificationId)
                .addValue("deliveryId", deliveryId)
                .addValue("eventType", eventType)
                .addValue("payloadJson", payloadJson)
                .addValue("createdAt", createdAt));
    }
}

