package com.stockvision.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * Consistent API error response envelope across validation, security, domain, and server errors.
 */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String traceId,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<FieldErrorDetail> fieldErrors) {

    public static ApiErrorResponse of(
            Instant timestamp,
            int status,
            String code,
            String message,
            String path,
            String traceId) {
        return new ApiErrorResponse(timestamp, status, code, message, path, traceId, null);
    }

    public static ApiErrorResponse of(
            Instant timestamp,
            int status,
            String code,
            String message,
            String path,
            String traceId,
            List<FieldErrorDetail> fieldErrors) {
        return new ApiErrorResponse(timestamp, status, code, message, path, traceId, fieldErrors);
    }

    /**
     * Individual field validation failure detail.
     */
    public record FieldErrorDetail(String field, String message) {
    }
}
