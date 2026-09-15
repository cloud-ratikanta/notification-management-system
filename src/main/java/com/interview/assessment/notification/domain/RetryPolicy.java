package com.interview.assessment.notification.domain;

import com.interview.assessment.notification.domain.enums.FailureClass;
import com.interview.assessment.notification.config.NotificationProperties;

import java.util.Optional;
import java.util.Random;

/**
 * Retry policy: decides if a FailureClass is retryable and computes next delay (ms)
 * using exponential backoff with full jitter capped by maxDelay.
 */
public class RetryPolicy {

    private final int maxAttempts;
    private final long baseDelayMs;
    private final long maxDelayMs;
    private final Random rng;

    public RetryPolicy(NotificationProperties.Retry props, Random rng) {
        this.maxAttempts = props.getMaxAttempts();
        this.baseDelayMs = props.getBaseDelayMs();
        this.maxDelayMs = props.getMaxDelayMs();
        this.rng = rng == null ? new Random() : rng;
    }

    public boolean isRetryable(FailureClass fc) {
        return switch (fc) {
            case TRANSIENT_PROVIDER_FAILURE, TIMEOUT, RATE_LIMIT, UNKNOWN -> true;
            default -> false;
        };
    }

    /**
     * Compute next delay (ms) for given attempt number (1-based). If retryAfterSeconds present and failure is RATE_LIMIT,
     * honor retry-after (converted to ms) as minimum.
     */
    public long nextDelayMs(int attemptNo, FailureClass fc, Optional<Long> retryAfterSeconds) {
        if (attemptNo <= 0) attemptNo = 1;
        long exp = baseDelayMs * (1L << (Math.max(0, attemptNo - 1)));
        long jitter = (long) (rng.nextDouble() * exp);
        long delay = Math.min(exp + jitter, maxDelayMs);
        if (fc == FailureClass.RATE_LIMIT && retryAfterSeconds.isPresent()) {
            long raMs = retryAfterSeconds.get() * 1000L;
            if (raMs > delay) delay = Math.min(raMs, maxDelayMs);
        }
        return delay;
    }

    public int getMaxAttempts() { return maxAttempts; }
}

