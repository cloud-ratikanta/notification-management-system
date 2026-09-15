package com.interview.assessment.notification.persistence;

import com.interview.assessment.notification.domain.enums.Channel;
import com.interview.assessment.notification.domain.enums.DeliveryStatus;
import com.interview.assessment.notification.domain.enums.FailureClass;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class DeliveryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DeliveryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(DeliveryRow row) {
        String sql = """
                INSERT INTO delivery (
                  id, notification_id, recipient_id, channel, status, attempt_count,
                  next_attempt_at, last_error_class, last_attempt_at, created_at, updated_at
                ) VALUES (
                  :id, :notificationId, :recipientId, :channel, :status, :attemptCount,
                  :nextAttemptAt, :lastErrorClass, :lastAttemptAt, :createdAt, :updatedAt
                )
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", row.id())
                .addValue("notificationId", row.notificationId())
                .addValue("recipientId", row.recipientId())
                .addValue("channel", row.channel().name())
                .addValue("status", row.status().name())
                .addValue("attemptCount", row.attemptCount())
                .addValue("nextAttemptAt", row.nextAttemptAt())
                .addValue("lastErrorClass", row.lastErrorClass() == null ? null : row.lastErrorClass().name())
                .addValue("lastAttemptAt", row.lastAttemptAt())
                .addValue("createdAt", row.createdAt())
                .addValue("updatedAt", row.updatedAt());

        jdbcTemplate.update(sql, params);
    }

    public List<DeliveryRow> findByNotificationId(UUID notificationId) {
        String sql = "SELECT * FROM delivery WHERE notification_id = :notificationId ORDER BY created_at";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("notificationId", notificationId),
                (rs, rowNum) -> mapRow(rs));
    }

    public List<DeliveryRow> claimQueuedBatch(int batchSize, Instant now) {
        String selectSql = """
                SELECT * FROM delivery
                WHERE status = :queuedStatus
                ORDER BY created_at
                LIMIT :batchSize
                """;

        List<DeliveryRow> queuedRows = jdbcTemplate.query(selectSql,
                new MapSqlParameterSource()
                        .addValue("queuedStatus", DeliveryStatus.PENDING.name())
                        .addValue("batchSize", batchSize),
                (rs, rowNum) -> mapRow(rs));

        for (DeliveryRow row : queuedRows) {
            String updateSql = """
                    UPDATE delivery
                    SET status = :newStatus,
                        attempt_count = attempt_count + 1,
                        last_attempt_at = :now,
                        updated_at = :now
                    WHERE id = :id AND status = :oldStatus
                    """;
            jdbcTemplate.update(updateSql, new MapSqlParameterSource()
                    .addValue("newStatus", DeliveryStatus.IN_FLIGHT.name())
                    .addValue("now", now)
                    .addValue("id", row.id())
                    .addValue("oldStatus", DeliveryStatus.PENDING.name()));
        }

        return findInflightByTime(now);
    }

    private List<DeliveryRow> findInflightByTime(Instant now) {
        String sql = "SELECT * FROM delivery WHERE status = :status AND updated_at = :updatedAt";
        return jdbcTemplate.query(sql,
                new MapSqlParameterSource("status", DeliveryStatus.IN_FLIGHT.name()).addValue("updatedAt", now),
                (rs, rowNum) -> mapRow(rs));
    }

    public void markSucceeded(UUID deliveryId, Instant now) {
        String sql = """
                UPDATE delivery
                SET status = :status,
                    updated_at = :updatedAt
                WHERE id = :id
                """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("status", DeliveryStatus.SUCCEEDED.name())
                .addValue("updatedAt", now)
                .addValue("id", deliveryId));
    }

    public void markFailed(UUID deliveryId, FailureClass failureClass, Instant now) {
        String sql = """
                UPDATE delivery
                SET status = :status,
                    last_error_class = :lastErrorClass,
                    updated_at = :updatedAt
                WHERE id = :id
                """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("status", DeliveryStatus.FAILED_TERMINAL.name())
                .addValue("lastErrorClass", failureClass.name())
                .addValue("updatedAt", now)
                .addValue("id", deliveryId));
    }

    private DeliveryRow mapRow(ResultSet rs) throws SQLException {
        String lastError = rs.getString("last_error_class");
        return new DeliveryRow(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("notification_id")),
                rs.getString("recipient_id"),
                Channel.valueOf(rs.getString("channel")),
                DeliveryStatus.valueOf(rs.getString("status")),
                rs.getInt("attempt_count"),
                toInstant(rs, "next_attempt_at"),
                lastError == null ? null : FailureClass.valueOf(lastError),
                toInstant(rs, "last_attempt_at"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    private Instant toInstant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column) == null ? null : rs.getTimestamp(column).toInstant();
    }

    public record DeliveryRow(
            UUID id,
            UUID notificationId,
            String recipientId,
            Channel channel,
            DeliveryStatus status,
            int attemptCount,
            Instant nextAttemptAt,
            FailureClass lastErrorClass,
            Instant lastAttemptAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}

