package com.interview.assessment.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("acceptance")
@AutoConfigureMockMvc
class NotificationApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void submitThenGetStatus() throws Exception {
        String payload = """
                {
                  "sourceSystem":"billing-service",
                  "eventId":"evt-1",
                  "type":"PAYMENT_FAILED",
                  "severity":"HIGH",
                  "priority":"NORMAL",
                  "recipients":[{"recipientId":"user-1","email":"user1@example.com"}],
                  "requestedChannels":["EMAIL"]
                }
                """;

        MvcResult postResult = mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k-1")
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn();

        JsonNode json = objectMapper.readTree(postResult.getResponse().getContentAsString());
        String id = json.get("notificationId").asText();
        assertThat(id).isNotBlank();

        mockMvc.perform(get("/api/v1/notifications/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationId").value(id));

        Integer notificationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE id = ?",
                Integer.class,
                id
        );
        Integer deliveryCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM delivery WHERE notification_id = ?",
                Integer.class,
                java.util.UUID.fromString(id)
        );
        assertThat(notificationCount).isEqualTo(1);
        assertThat(deliveryCount).isEqualTo(1);
    }

    @Test
    void sameIdempotencyKeyWithSamePayloadReturnsReplay() throws Exception {
        String payload = """
                {
                  "sourceSystem":"ops-service",
                  "eventId":"evt-2",
                  "type":"DISK_ALERT",
                  "severity":"HIGH",
                  "priority":"NORMAL",
                  "recipients":[{"recipientId":"user-2","email":"user2@example.com"}],
                  "requestedChannels":["EMAIL"]
                }
                """;

        MvcResult first = mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "same-key")
                        .content(payload))
                .andExpect(status().isAccepted())
                .andReturn();

        JsonNode firstJson = objectMapper.readTree(first.getResponse().getContentAsString());
        String id = firstJson.get("notificationId").asText();

        mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "same-key")
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationId").value(id))
                .andExpect(jsonPath("$.duplicate").value(true));
    }

    @Test
    void sameIdempotencyKeyWithDifferentPayloadReturnsConflict() throws Exception {
        String payload1 = """
                {
                  "sourceSystem":"monitoring-service",
                  "eventId":"evt-3",
                  "type":"CPU_ALERT",
                  "severity":"HIGH",
                  "priority":"NORMAL",
                  "recipients":[{"recipientId":"user-3","email":"user3@example.com"}],
                  "requestedChannels":["EMAIL"]
                }
                """;

        String payload2 = """
                {
                  "sourceSystem":"monitoring-service",
                  "eventId":"evt-3-diff",
                  "type":"CPU_ALERT",
                  "severity":"HIGH",
                  "priority":"NORMAL",
                  "recipients":[{"recipientId":"user-3","email":"user3@example.com"}],
                  "requestedChannels":["EMAIL"]
                }
                """;

        mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "conflict-key")
                        .content(payload1))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "conflict-key")
                        .content(payload2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void differentIdempotencyKeysSameContentSecondSuppressed() throws Exception {
        String payload = """
                {
                  "sourceSystem":"billing-service",
                  "eventId":"evt-dup-1",
                  "type":"PAYMENT_FAILED",
                  "severity":"HIGH",
                  "priority":"NORMAL",
                  "recipients":[{"recipientId":"user-dup","email":"dup@example.com"}],
                  "requestedChannels":["EMAIL"]
                }
                """;

        MvcResult first = mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "dup-key-1")
                        .content(payload))
                .andExpect(status().isAccepted())
                .andReturn();

        String firstId = objectMapper.readTree(first.getResponse().getContentAsString()).get("notificationId").asText();

        // second POST with different idempotency key but identical content -> should be suppressed (replay)
        mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "dup-key-2")
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationId").value(firstId))
                .andExpect(jsonPath("$.duplicate").value(true));

        Integer notificationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE id = ?",
                Integer.class,
                firstId
        );
        Integer deliveryCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM delivery WHERE notification_id = ?",
                Integer.class,
                java.util.UUID.fromString(firstId)
        );
        assertThat(notificationCount).isEqualTo(1);
        assertThat(deliveryCount).isEqualTo(1);
    }

    @Test
    void auditEndpointReturnsEventsForNotification() throws Exception {
        String payload = """
                {
                  "sourceSystem":"audit-source",
                  "eventId":"evt-audit",
                  "type":"AUDIT_TEST",
                  "severity":"HIGH",
                  "priority":"NORMAL",
                  "recipients":[{"recipientId":"user-5","email":"user5@example.com"}],
                  "requestedChannels":["EMAIL"]
                }
                """;

        MvcResult postResult = mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "audit-key")
                        .content(payload))
                .andExpect(status().isAccepted())
                .andReturn();

        String id = objectMapper.readTree(postResult.getResponse().getContentAsString()).get("notificationId").asText();

        mockMvc.perform(get("/api/v1/notifications/{id}/audit", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").exists());
    }
}

