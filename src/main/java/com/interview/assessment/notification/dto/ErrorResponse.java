package com.interview.assessment.notification.dto;

import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        String correlationId,
        List<FieldErrorItem> details
) {
    public record FieldErrorItem(String field, String reason) {
    }
}

