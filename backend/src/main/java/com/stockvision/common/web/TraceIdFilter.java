package com.stockvision.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns or validates a correlation {@code X-Trace-Id} for each HTTP request, stores it in
 * SLF4J MDC and request attributes, echoes it on the response, and clears MDC after execution.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";
    public static final String REQUEST_ATTRIBUTE = "traceId";
    private static final Pattern VALID_TRACE_ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = resolveOrCreateTraceId(request.getHeader(HEADER_NAME));
        MDC.put(MDC_KEY, traceId);
        request.setAttribute(REQUEST_ATTRIBUTE, traceId);
        response.setHeader(HEADER_NAME, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    public static String resolveCurrentTraceId(HttpServletRequest request) {
        if (request != null) {
            Object attr = request.getAttribute(REQUEST_ATTRIBUTE);
            if (attr instanceof String attrTraceId && !attrTraceId.isBlank()) {
                return attrTraceId;
            }
        }
        String mdcTraceId = MDC.get(MDC_KEY);
        if (mdcTraceId != null && !mdcTraceId.isBlank()) {
            return mdcTraceId;
        }
        return UUID.randomUUID().toString();
    }

    private static String resolveOrCreateTraceId(String incomingHeader) {
        if (incomingHeader != null && VALID_TRACE_ID.matcher(incomingHeader).matches()) {
            return incomingHeader;
        }
        return UUID.randomUUID().toString();
    }
}
