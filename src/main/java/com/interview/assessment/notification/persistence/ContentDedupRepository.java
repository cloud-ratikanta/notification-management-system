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
public class ContentDedupRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ContentDedupRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ContentDedupRecord> findRecentByHash(String contentHash, Instant since) {
        String sql = """
                SELECT id, content_hash, notification_id, created_at, expires_at
                FROM content_dedup
                WHERE content_hash = :contentHash AND created_at >= :since
                ORDER BY created_at DESC
                """;
        List<ContentDedupRecord> rows = jdbcTemplate.query(sql, new MapSqlParameterSource()
                        .addValue("contentHash", contentHash)
                        .addValue("since", Timestamp.from(since)),
                (rs, rowNum) -> mapRow(rs));
        return rows.stream().findFirst();
    }

    public void insert(ContentDedupRecord r) {
        String sql = """
                INSERT INTO content_dedup (id, content_hash, notification_id, created_at, expires_at)
                VALUES (:id, :contentHash, :notificationId, :createdAt, :expiresAt)
                """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("id", r.id().toString())
                .addValue("contentHash", r.contentHash())
                .addValue("notificationId", r.notificationId().toString())
                .addValue("createdAt", Timestamp.from(r.createdAt()))
                .addValue("expiresAt", Timestamp.from(r.expiresAt())));
    }

    private ContentDedupRecord mapRow(ResultSet rs) throws SQLException {
        return new ContentDedupRecord(
                UUID.fromString(rs.getString("id")),
                rs.getString("content_hash"),
                UUID.fromString(rs.getString("notification_id")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant()
        );
    }

    public record ContentDedupRecord(UUID id, String contentHash, UUID notificationId, Instant createdAt, Instant expiresAt) {
    }
}

