package com.stockvision.common.api;

import com.stockvision.common.api.ApiErrorResponse.FieldErrorDetail;
import com.stockvision.common.exception.AppException;
import com.stockvision.common.exception.ErrorCode;
import com.stockvision.common.web.LogSanitizer;
import com.stockvision.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Translates application, validation, security, and unexpected exceptions into uniform
 * {@link ApiErrorResponse} payloads without exposing internal stack traces or sensitive data.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiErrorResponse> handleAppException(
            AppException ex, HttpServletRequest request) {
        return buildResponse(ex.getErrorCode(), ex.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldErrorDetail> fieldErrors =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(
                                error ->
                                        new FieldErrorDetail(
                                                error.getField(),
                                                error.getDefaultMessage() != null
                                                        ? error.getDefaultMessage()
                                                        : "Invalid value"))
                        .sorted(
                                Comparator.comparing(FieldErrorDetail::field)
                                        .thenComparing(FieldErrorDetail::message))
                        .toList();
        return buildResponse(
                ErrorCode.VALIDATION_ERROR, "Request validation failed", request, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        List<FieldErrorDetail> fieldErrors =
                ex.getConstraintViolations().stream()
                        .map(
                                violation ->
                                        new FieldErrorDetail(
                                                violation.getPropertyPath().toString(),
                                                violation.getMessage()))
                        .sorted(
                                Comparator.comparing(FieldErrorDetail::field)
                                        .thenComparing(FieldErrorDetail::message))
                        .toList();
        return buildResponse(
                ErrorCode.VALIDATION_ERROR, "Request validation failed", request, fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return buildResponse(
                ErrorCode.MALFORMED_REQUEST,
                "Malformed or unreadable request body",
                request,
                null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        return buildResponse(
                ErrorCode.BAD_REQUEST,
                "Missing required parameter: " + ex.getParameterName(),
                request,
                null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return buildResponse(
                ErrorCode.BAD_REQUEST,
                "Invalid value for parameter: " + ex.getName(),
                request,
                null);
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ApiErrorResponse> handleRequestBinding(
            ServletRequestBindingException ex, HttpServletRequest request) {
        return buildResponse(
                ErrorCode.BAD_REQUEST, "Invalid request parameter or header binding", request, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return buildResponse(
                ErrorCode.METHOD_NOT_ALLOWED,
                "HTTP method is not supported for this endpoint",
                request,
                null);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            Exception ex, HttpServletRequest request) {
        return buildResponse(ErrorCode.NOT_FOUND, "Resource not found", request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        return buildResponse(ErrorCode.FORBIDDEN, "Access denied", request, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {
        return buildResponse(ErrorCode.UNAUTHORIZED, "Authentication required", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception ex, HttpServletRequest request) {
        log.error(
                "Unhandled exception at path={} type={} detail={}",
                LogSanitizer.redact(request.getRequestURI()),
                ex.getClass().getSimpleName(),
                LogSanitizer.redact(ex.getMessage()));
        return buildResponse(
                ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", request, null);
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
            ErrorCode errorCode,
            String message,
            HttpServletRequest request,
            List<FieldErrorDetail> fieldErrors) {
        ApiErrorResponse body =
                ApiErrorResponse.of(
                        Instant.now(clock),
                        errorCode.getStatus().value(),
                        errorCode.getCode(),
                        message,
                        request.getRequestURI(),
                        TraceIdFilter.resolveCurrentTraceId(request),
                        fieldErrors);
        return ResponseEntity.status(errorCode.getStatus()).body(body);
    }
}
