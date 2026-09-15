package com.interview.assessment.notification;

import com.interview.assessment.notification.service.AuditSanitizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditSanitizerTest {

    @Test
    void redacts_token_and_long_body() throws Exception {
        ObjectMapper m = new ObjectMapper();
        AuditSanitizer s = new AuditSanitizer(m);
        String payload = "{" +
                "\"token\":\"secret-abc\"," +
                "\"body\":\"" + "x".repeat(300) + "\"}";

        String out = s.sanitize(payload);
        assertThat(out).doesNotContain("secret-abc");
        assertThat(out).contains("<REDACTED_BODY");
    }
}

