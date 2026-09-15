package com.interview.assessment.notification.persistence;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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
                .addValue("createdAt", Timestamp.from(createdAt)));
    }

    public List<AuditEventRow> findByNotificationId(UUID notificationId) {
        String sql = """
                SELECT id, notification_id, delivery_id, event_type, payload_json, created_at
                FROM audit_event
                WHERE notification_id = :notificationId
                ORDER BY created_at, id
                """;
        return jdbcTemplate.query(sql,
                new MapSqlParameterSource("notificationId", notificationId),
                (rs, rowNum) -> mapRow(rs));
    }

    private AuditEventRow mapRow(ResultSet rs) throws SQLException {
        return new AuditEventRow(
                UUID.fromString(rs.getString("id")),
                rs.getString("notification_id") == null ? null : UUID.fromString(rs.getString("notification_id")),
                rs.getString("delivery_id") == null ? null : UUID.fromString(rs.getString("delivery_id")),
                rs.getString("event_type"),
                rs.getString("payload_json"),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    public record AuditEventRow(
            UUID id,
            UUID notificationId,
            UUID deliveryId,
            String eventType,
            String payloadJson,
            Instant createdAt
    ) {
    }
}

