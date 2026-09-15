package com.interview.assessment.notification;

import com.interview.assessment.notification.domain.FailureClassifier;
import com.interview.assessment.notification.domain.enums.FailureClass;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class FailureClassifierTest {

    @Test
    void maps_status_and_exceptions_to_failure_class() {
        FailureClassifier c = new FailureClassifier();
        assertThat(c.classify(500, null)).isEqualTo(FailureClass.TRANSIENT_PROVIDER_FAILURE);
        assertThat(c.classify(502, null)).isEqualTo(FailureClass.TRANSIENT_PROVIDER_FAILURE);
        assertThat(c.classify(429, null)).isEqualTo(FailureClass.RATE_LIMIT);
        assertThat(c.classify(401, null)).isEqualTo(FailureClass.AUTH_ERROR);
        assertThat(c.classify(404, null)).isEqualTo(FailureClass.PERMANENT_PROVIDER_REJECTION);
        assertThat(c.classify(null, new SocketTimeoutException())).isEqualTo(FailureClass.TIMEOUT);
    }
}

