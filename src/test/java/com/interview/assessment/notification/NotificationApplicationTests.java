package com.interview.assessment.notification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "notification.worker.enabled=false")
class NotificationApplicationTests {

    @Test
    void contextLoads() {
    }
}

