package com.stockvision.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Emits structured HTTP access telemetry paired with the MDC {@code traceId}. Never logs query
 * parameters, request bodies, authorization headers, or personal data.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestAuditLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestAuditLoggingFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        long startNanos = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            log.info(
                    "event=http_request method={} path={} status={} durationMs={}",
                    request.getMethod(),
                    LogSanitizer.redact(request.getRequestURI()),
                    response.getStatus(),
                    durationMs);
        }
    }
}
