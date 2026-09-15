package com.interview.assessment.notification.persistence;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class IdempotencyRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public IdempotencyRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<IdempotencyRecord> find(String sourceSystem, String idempotencyKey) {
        String sql = """
                SELECT source_system, idempotency_key, notification_id, request_hash, created_at, expires_at
                FROM idempotency_record
                WHERE source_system = :sourceSystem AND idempotency_key = :idempotencyKey
                """;
        List<IdempotencyRecord> rows = jdbcTemplate.query(sql,
                new MapSqlParameterSource()
                        .addValue("sourceSystem", sourceSystem)
                        .addValue("idempotencyKey", idempotencyKey),
                (rs, rowNum) -> mapRow(rs));
        return rows.stream().findFirst();
    }

    
    // legacy-compatible: original find without expiry filter (not used internally)
    public Optional<IdempotencyRecord> findAllowExpired(String sourceSystem, String idempotencyKey) {
        String sql = """
                SELECT source_system, idempotency_key, notification_id, request_hash, created_at, expires_at
                FROM idempotency_record
                WHERE source_system = :sourceSystem AND idempotency_key = :idempotencyKey
                """;
        List<IdempotencyRecord> rows = jdbcTemplate.query(sql,
                new MapSqlParameterSource()
                        .addValue("sourceSystem", sourceSystem)
                        .addValue("idempotencyKey", idempotencyKey),
                (rs, rowNum) -> mapRow(rs));
        return rows.stream().findFirst();
    }

    public void insert(IdempotencyRecord record) {
        String sql = """
                INSERT INTO idempotency_record (
                  source_system, idempotency_key, notification_id, request_hash, created_at, expires_at
                ) VALUES (
                  :sourceSystem, :idempotencyKey, :notificationId, :requestHash, :createdAt, :expiresAt
                )
                """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("sourceSystem", record.sourceSystem())
                .addValue("idempotencyKey", record.idempotencyKey())
                .addValue("notificationId", record.notificationId())
                .addValue("requestHash", record.requestHash())
                .addValue("createdAt", Timestamp.from(record.createdAt()))
                .addValue("expiresAt", Timestamp.from(record.expiresAt())));
    }

    public void delete(String sourceSystem, String idempotencyKey) {
        String sql = """
                DELETE FROM idempotency_record
                WHERE source_system = :sourceSystem AND idempotency_key = :idempotencyKey
                """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("sourceSystem", sourceSystem)
                .addValue("idempotencyKey", idempotencyKey));
    }

    private IdempotencyRecord mapRow(ResultSet rs) throws SQLException {
        return new IdempotencyRecord(
                rs.getString("source_system"),
                rs.getString("idempotency_key"),
                UUID.fromString(rs.getString("notification_id")),
                rs.getString("request_hash"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant()
        );
    }

    public record IdempotencyRecord(
            String sourceSystem,
            String idempotencyKey,
            UUID notificationId,
            String requestHash,
            Instant createdAt,
            Instant expiresAt
    ) {
    }
}

