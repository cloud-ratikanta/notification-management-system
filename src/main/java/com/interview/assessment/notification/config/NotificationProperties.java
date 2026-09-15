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

    private final Dedup dedup = new Dedup();
    private final Idempotency idempotency = new Idempotency();
    private final Retry retry = new Retry();

    public Dedup getDedup() { return dedup; }
    public Idempotency getIdempotency() { return idempotency; }
    public Retry getRetry() { return retry; }

    public static class Dedup {
        /** dedup window in seconds (default 24h) */
        private long windowSeconds = 24 * 3600L;

        /** whether eventId should be considered when computing the content hash */
        private boolean includeEventId = false;

        private boolean includeTitle = true;
        private boolean includeBody = true;
        private boolean includeTemplateParams = true;

        public long getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(long windowSeconds) {
            this.windowSeconds = windowSeconds;
        }

        public boolean isIncludeEventId() {
            return includeEventId;
        }

        public void setIncludeEventId(boolean includeEventId) {
            this.includeEventId = includeEventId;
        }

        public boolean isIncludeTitle() {
            return includeTitle;
        }

        public void setIncludeTitle(boolean includeTitle) {
            this.includeTitle = includeTitle;
        }

        public boolean isIncludeBody() {
            return includeBody;
        }

        public void setIncludeBody(boolean includeBody) {
            this.includeBody = includeBody;
        }

        public boolean isIncludeTemplateParams() {
            return includeTemplateParams;
        }

        public void setIncludeTemplateParams(boolean includeTemplateParams) {
            this.includeTemplateParams = includeTemplateParams;
        }
    }

    

    public static class Idempotency {
        private long ttlSeconds = 24 * 3600L;

        public long getTtlSeconds() { return ttlSeconds; }
        public void setTtlSeconds(long ttlSeconds) { this.ttlSeconds = ttlSeconds; }
    }

    public static class Retry {
        private int maxAttempts = 5;
        private long baseDelayMs = 1000L;
        private long maxDelayMs = 5 * 60 * 1000L; // 5 minutes

        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

        public long getBaseDelayMs() { return baseDelayMs; }
        public void setBaseDelayMs(long baseDelayMs) { this.baseDelayMs = baseDelayMs; }

        public long getMaxDelayMs() { return maxDelayMs; }
        public void setMaxDelayMs(long maxDelayMs) { this.maxDelayMs = maxDelayMs; }
    }
}

