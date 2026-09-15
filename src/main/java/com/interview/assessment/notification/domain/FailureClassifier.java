package com.interview.assessment.notification.domain;

import com.interview.assessment.notification.domain.enums.FailureClass;

import java.net.SocketTimeoutException;
import java.util.Optional;

/**
 * Map provider signals (HTTP status codes / exceptions) to FailureClass.
 */
public class FailureClassifier {

    public FailureClass classify(Integer httpStatus, Throwable ex) {
        if (ex != null) {
            if (ex instanceof SocketTimeoutException) return FailureClass.TIMEOUT;
            // other exception-based heuristics can be added here
        }

        if (httpStatus == null) return FailureClass.UNKNOWN;

        if (httpStatus == 429) return FailureClass.RATE_LIMIT;
        if (httpStatus >= 500 && httpStatus < 600) return FailureClass.TRANSIENT_PROVIDER_FAILURE;
        if (httpStatus == 401 || httpStatus == 403) return FailureClass.AUTH_ERROR;
        if (httpStatus >= 400 && httpStatus < 500) return FailureClass.PERMANENT_PROVIDER_REJECTION;

        return FailureClass.UNKNOWN;
    }
}

