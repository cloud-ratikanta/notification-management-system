package com.interview.assessment.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.assessment.notification.worker.DeliveryWorker;
import com.interview.assessment.notification.persistence.DeliveryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"notification.worker.enabled=true"})
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("acceptance")
class E2EWorkerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DeliveryWorker deliveryWorker;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Test
    void submit_then_worker_ticks_and_delivery_succeeds() throws Exception {
        String payload = """
                {
                  "sourceSystem":"billing-service",
                  "eventId":"evt-e2e",
                  "type":"PAYMENT_FAILED",
                  "severity":"HIGH",
                  "priority":"NORMAL",
                  "recipients":[{"recipientId":"user-e2e","email":"user-e2e@example.com"}],
                  "requestedChannels":["EMAIL"]
                }
                """;

        var mvcResult = mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted())
                .andReturn();

        JsonNode json = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
        UUID id = UUID.fromString(json.get("notificationId").asText());

        // Run worker synchronously until delivery completes (poll with small timeout)
        boolean succeeded = false;
        long deadline = System.currentTimeMillis() + 5_000; // 5s timeout
        while (System.currentTimeMillis() < deadline) {
            deliveryWorker.tick();
            Thread.sleep(100);
            var deliveries = deliveryRepository.findByNotificationId(id);
            if (!deliveries.isEmpty()) {
                if (deliveries.stream().anyMatch(d -> d.status() == com.interview.assessment.notification.domain.enums.DeliveryStatus.SUCCEEDED)) {
                    succeeded = true;
                    break;
                }
            }
        }

        var deliveries = deliveryRepository.findByNotificationId(id);
        assertThat(deliveries).isNotEmpty();
        assertThat(succeeded).as("expected at least one delivery to reach SUCCEEDED within timeout").isTrue();
    }
}

