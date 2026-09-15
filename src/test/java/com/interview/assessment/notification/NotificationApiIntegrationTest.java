package com.interview.assessment.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "notification.worker.enabled=false")
@AutoConfigureMockMvc
class NotificationApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    }
}

