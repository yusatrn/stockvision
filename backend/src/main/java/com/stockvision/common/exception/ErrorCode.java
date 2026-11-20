package com.stockvision.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Standardized machine-readable error codes returned by the StockVision AI API.
 */
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "BAD_REQUEST"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED"),
    CONFLICT(HttpStatus.CONFLICT, "CONFLICT"),
    INSUFFICIENT_DATA(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_DATA"),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS"),
    PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_UNAVAILABLE"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");

    private final HttpStatus status;
    private final String code;

    ErrorCode(HttpStatus status, String code) {
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
