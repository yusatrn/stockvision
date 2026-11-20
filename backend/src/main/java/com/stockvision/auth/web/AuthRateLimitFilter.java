package com.stockvision.auth.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockvision.auth.domain.AuthRateLimiter;
import com.stockvision.auth.domain.RateLimitDecision;
import com.stockvision.auth.infrastructure.RateLimitProperties;
import com.stockvision.common.api.ApiErrorResponse;
import com.stockvision.common.exception.ErrorCode;
import com.stockvision.common.web.TraceIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Enforces basic rate limiting on authentication endpoints ({@code POST /api/v1/auth/login} and
 * {@code POST /api/v1/auth/register}).
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> PROTECTED_PATHS =
            Set.of(
                    "/api/v1/auth/login",
                    "/api/v1/auth/register",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/logout");

    private final AuthRateLimiter authRateLimiter;
    private final RateLimitProperties rateLimitProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuthRateLimitFilter(
            AuthRateLimiter authRateLimiter,
            RateLimitProperties rateLimitProperties,
            ObjectMapper objectMapper,
            Clock clock) {
        this.authRateLimiter = authRateLimiter;
        this.rateLimitProperties = rateLimitProperties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        String requestPath = request.getRequestURI();
        if (rateLimitProperties.isEnabled()
                && HttpMethod.POST.matches(request.getMethod())
                && PROTECTED_PATHS.contains(requestPath)) {
            String remoteAddress =
                    request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
            String bucketKey = remoteAddress + ":" + requestPath;
            RateLimitDecision decision = authRateLimiter.tryConsume(bucketKey);
            if (!decision.allowed()) {
                writeRateLimitResponse(request, response, decision.retryAfterSeconds());
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private void writeRateLimitResponse(
            HttpServletRequest request, HttpServletResponse response, long retryAfterSeconds)
            throws IOException {
        ApiErrorResponse errorResponse =
                ApiErrorResponse.of(
                        Instant.now(clock),
                        ErrorCode.TOO_MANY_REQUESTS.getStatus().value(),
                        ErrorCode.TOO_MANY_REQUESTS.getCode(),
                        "Too many authentication requests. Please retry later.",
                        request.getRequestURI(),
                        TraceIdFilter.resolveCurrentTraceId(request));
        response.setStatus(ErrorCode.TOO_MANY_REQUESTS.getStatus().value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
