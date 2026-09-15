package com.interview.assessment.notification.persistence;

import com.interview.assessment.notification.domain.enums.Channel;
import com.interview.assessment.notification.domain.enums.NotificationStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class NotificationRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public NotificationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(NotificationRow row) {
        String sql = """
                INSERT INTO notification (
                  id, source_system, event_id, correlation_id, type, severity, priority,
                  status, selected_channels, created_at, updated_at
                ) VALUES (
                  :id, :sourceSystem, :eventId, :correlationId, :type, :severity, :priority,
                  :status, :selectedChannels, :createdAt, :updatedAt
                )
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", row.id())
                .addValue("sourceSystem", row.sourceSystem())
                .addValue("eventId", row.eventId())
                .addValue("correlationId", row.correlationId())
                .addValue("type", row.type())
                .addValue("severity", row.severity())
                .addValue("priority", row.priority())
                .addValue("status", row.status().name())
                .addValue("selectedChannels", encodeChannels(row.selectedChannels()))
                .addValue("createdAt", row.createdAt())
                .addValue("updatedAt", row.updatedAt());

        jdbcTemplate.update(sql, params);
    }

    public Optional<NotificationRow> findById(UUID notificationId) {
        String sql = "SELECT * FROM notification WHERE id = :id";
        List<NotificationRow> rows = jdbcTemplate.query(sql,
                new MapSqlParameterSource("id", notificationId),
                (rs, rowNum) -> mapRow(rs));
        return rows.stream().findFirst();
    }

    private NotificationRow mapRow(ResultSet rs) throws SQLException {
        return new NotificationRow(
                UUID.fromString(rs.getString("id")),
                rs.getString("source_system"),
                rs.getString("event_id"),
                rs.getString("correlation_id"),
                rs.getString("type"),
                rs.getString("severity"),
                rs.getString("priority"),
                NotificationStatus.valueOf(rs.getString("status")),
                decodeChannels(rs.getString("selected_channels")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    private String encodeChannels(List<Channel> channels) {
        return channels.stream().map(Enum::name).reduce((a, b) -> a + "," + b).orElse("");
    }

    private List<Channel> decodeChannels(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isBlank()).map(Channel::valueOf).toList();
    }

    public record NotificationRow(
            UUID id,
            String sourceSystem,
            String eventId,
            String correlationId,
            String type,
            String severity,
            String priority,
            NotificationStatus status,
            List<Channel> selectedChannels,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}

