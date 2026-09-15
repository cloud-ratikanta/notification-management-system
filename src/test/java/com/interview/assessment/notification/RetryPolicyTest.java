package com.interview.assessment.notification;

import com.interview.assessment.notification.config.NotificationProperties;
import com.interview.assessment.notification.domain.RetryPolicy;
import com.interview.assessment.notification.domain.enums.FailureClass;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {

    @Test
    void backoff_grows_with_attempts_and_honors_retry_after_for_rate_limit() {
        NotificationProperties props = new NotificationProperties();
        props.getRetry().setBaseDelayMs(100L);
        props.getRetry().setMaxDelayMs(20000L);
        RetryPolicy p = new RetryPolicy(props.getRetry(), new Random(12345L));

        long d1 = p.nextDelayMs(1, FailureClass.TRANSIENT_PROVIDER_FAILURE, Optional.empty());
        long d2 = p.nextDelayMs(2, FailureClass.TRANSIENT_PROVIDER_FAILURE, Optional.empty());
        long d3 = p.nextDelayMs(3, FailureClass.TRANSIENT_PROVIDER_FAILURE, Optional.empty());

        assertThat(d2).isGreaterThanOrEqualTo(d1);
        assertThat(d3).isGreaterThanOrEqualTo(d2);

        // Rate limit honors Retry-After when larger than computed delay
        long ra = 10L; // seconds
        long dr = p.nextDelayMs(1, FailureClass.RATE_LIMIT, Optional.of(ra));
        assertThat(dr).isGreaterThanOrEqualTo(ra * 1000L);
    }

    @Test
    void is_retryable_respects_classes() {
        NotificationProperties props = new NotificationProperties();
        RetryPolicy p = new RetryPolicy(props.getRetry(), new Random(1L));
        assertThat(p.isRetryable(FailureClass.TRANSIENT_PROVIDER_FAILURE)).isTrue();
        assertThat(p.isRetryable(FailureClass.TIMEOUT)).isTrue();
        assertThat(p.isRetryable(FailureClass.RATE_LIMIT)).isTrue();
        assertThat(p.isRetryable(FailureClass.INVALID_RECIPIENT)).isFalse();
    }
}

