package com.interview.assessment.notification.exception;

import com.interview.assessment.notification.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, jakarta.servlet.http.HttpServletRequest request) {
        List<ErrorResponse.FieldErrorItem> details = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldError)
                .toList();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                new ErrorResponse("VALIDATION_FAILED", "Request validation failed", extractCorrelationId(request), details)
        );
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, jakarta.servlet.http.HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                new ErrorResponse("NOT_FOUND", ex.getMessage(), extractCorrelationId(request), List.of())
        );
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex, jakarta.servlet.http.HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                new ErrorResponse("BAD_REQUEST", ex.getMessage(), extractCorrelationId(request), List.of())
        );
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex, jakarta.servlet.http.HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                new ErrorResponse("IDEMPOTENCY_CONFLICT", ex.getMessage(), extractCorrelationId(request), List.of())
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, jakarta.servlet.http.HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                new ErrorResponse("INTERNAL_ERROR", "Unexpected server error", extractCorrelationId(request), List.of())
        );
    }

    private ErrorResponse.FieldErrorItem toFieldError(FieldError error) {
        return new ErrorResponse.FieldErrorItem(error.getField(), error.getDefaultMessage());
    }

    private String extractCorrelationId(jakarta.servlet.http.HttpServletRequest request) {
        if (request == null) return null;
        String c = request.getHeader("Correlation-Id");
        if (c != null && !c.isBlank()) return c;
        c = request.getHeader("X-Correlation-Id");
        return c == null || c.isBlank() ? null : c;
    }
}

