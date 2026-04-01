package com.taskflow.dto.response;

import org.springframework.http.HttpStatus;

import java.time.Instant;

public record ApiErrorResponse(
        int code,
        String status,
        String message,
        Object errors,
        Instant timestamp
) {
    public static ApiErrorResponse of(HttpStatus status, String message, Object errors) {
        return new ApiErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                message,
                errors,
                Instant.now()
        );
    }
}
