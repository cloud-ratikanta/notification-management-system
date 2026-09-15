package com.interview.assessment.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {

    private final Worker worker = new Worker();

    public Worker getWorker() {
        return worker;
    }

    public static class Worker {
        /**
         * When false, DeliveryWorker is not registered (useful for HTTP-only tests).
         */
        private boolean enabled = true;
        private long fixedDelayMs = 1000L;
        private int batchSize = 20;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getFixedDelayMs() {
            return fixedDelayMs;
        }

        public void setFixedDelayMs(long fixedDelayMs) {
            this.fixedDelayMs = fixedDelayMs;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }
}

