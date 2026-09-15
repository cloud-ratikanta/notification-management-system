package com.interview.assessment.notification.persistence;

import com.interview.assessment.notification.domain.enums.Channel;
import com.interview.assessment.notification.domain.enums.DeliveryStatus;
import com.interview.assessment.notification.domain.enums.FailureClass;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
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
                .addValue("nextAttemptAt", toTimestamp(row.nextAttemptAt()))
                .addValue("lastErrorClass", row.lastErrorClass() == null ? null : row.lastErrorClass().name())
                .addValue("lastAttemptAt", toTimestamp(row.lastAttemptAt()))
                .addValue("createdAt", Timestamp.from(row.createdAt()))
                .addValue("updatedAt", Timestamp.from(row.updatedAt()));

        jdbcTemplate.update(sql, params);
    }

    public List<DeliveryRow> findByNotificationId(UUID notificationId) {
        String sql = baseSelect() + " WHERE d.notification_id = :notificationId ORDER BY d.created_at";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("notificationId", notificationId),
                (rs, rowNum) -> mapRow(rs));
    }

    public List<DeliveryRow> claimQueuedBatch(int batchSize, Instant now) {
        String selectSql = baseSelect() + """
                 WHERE d.status IN (:pendingStatus, :retryStatus)
                   AND d.next_attempt_at <= :now
                 ORDER BY d.next_attempt_at, d.created_at
                 LIMIT :batchSize
                """;

        List<DeliveryRow> candidates = jdbcTemplate.query(selectSql,
                new MapSqlParameterSource()
                        .addValue("pendingStatus", DeliveryStatus.PENDING.name())
                        .addValue("retryStatus", DeliveryStatus.RETRY_SCHEDULED.name())
                        .addValue("now", Timestamp.from(now))
                        .addValue("batchSize", batchSize),
                (rs, rowNum) -> mapRow(rs));

        List<UUID> claimedIds = new ArrayList<>();
        for (DeliveryRow row : candidates) {
            String updateSql = """
                    UPDATE delivery
                    SET status = :newStatus,
                        attempt_count = attempt_count + 1,
                        last_attempt_at = :now,
                        updated_at = :now
                    WHERE id = :id AND status IN (:pendingStatus, :retryStatus)
                    """;
            int updated = jdbcTemplate.update(updateSql, new MapSqlParameterSource()
                    .addValue("newStatus", DeliveryStatus.IN_FLIGHT.name())
                    .addValue("now", Timestamp.from(now))
                    .addValue("id", row.id())
                    .addValue("pendingStatus", DeliveryStatus.PENDING.name())
                    .addValue("retryStatus", DeliveryStatus.RETRY_SCHEDULED.name()));
            if (updated == 1) {
                claimedIds.add(row.id());
            }
        }

        if (claimedIds.isEmpty()) {
            return List.of();
        }

        String claimedSql = baseSelect() + " WHERE d.id IN (:ids)";
        return jdbcTemplate.query(claimedSql,
                new MapSqlParameterSource("ids", claimedIds),
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
                .addValue("updatedAt", Timestamp.from(now))
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
                .addValue("updatedAt", Timestamp.from(now))
                .addValue("id", deliveryId));
    }

    public void markExpired(UUID deliveryId, Instant now) {
        String sql = """
                UPDATE delivery
                SET status = :status,
                    updated_at = :updatedAt
                WHERE id = :id
                """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("status", DeliveryStatus.EXPIRED.name())
                .addValue("updatedAt", Timestamp.from(now))
                .addValue("id", deliveryId));
    }

    public void recordAttempt(UUID deliveryId,
                              int attemptNo,
                              String outcome,
                              FailureClass failureClass,
                              String safeDetail,
                              Instant startedAt,
                              Instant finishedAt) {
        String sql = """
                INSERT INTO delivery_attempt (id, delivery_id, attempt_no, started_at, finished_at, outcome, error_class, safe_detail)
                VALUES (:id, :deliveryId, :attemptNo, :startedAt, :finishedAt, :outcome, :errorClass, :safeDetail)
                """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("deliveryId", deliveryId)
                .addValue("attemptNo", attemptNo)
                .addValue("startedAt", Timestamp.from(startedAt))
                .addValue("finishedAt", Timestamp.from(finishedAt))
                .addValue("outcome", outcome)
                .addValue("errorClass", failureClass == null ? null : failureClass.name())
                .addValue("safeDetail", safeDetail));
    }

    private String baseSelect() {
        return """
                SELECT d.*, nr.email AS recipient_email, nr.phone AS recipient_phone, nr.slack_target AS recipient_slack_target
                FROM delivery d
                LEFT JOIN notification_recipient nr
                  ON nr.notification_id = d.notification_id
                 AND nr.recipient_ref = d.recipient_id
                """;
    }

    private Timestamp toTimestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
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
                rs.getString("recipient_email"),
                rs.getString("recipient_phone"),
                rs.getString("recipient_slack_target"),
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
            String recipientEmail,
            String recipientPhone,
            String recipientSlackTarget,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
