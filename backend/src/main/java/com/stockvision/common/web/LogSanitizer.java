package com.stockvision.common.web;

import java.util.regex.Pattern;

/**
 * Redacts credentials, bearer/JWT tokens, API keys, and email PII from diagnostic log messages.
 */
public final class LogSanitizer {

    private static final Pattern BEARER_PATTERN =
            Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern JWT_PATTERN =
            Pattern.compile("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("\\b[A-Za-z0-9._%+-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})\\b");
    private static final Pattern KEY_VALUE_SECRET_PATTERN =
            Pattern.compile(
                    "(?i)(password|secret|token|refreshToken|accessToken|apiKey|api_key)\\s*[:=]\\s*[^\\s,;]+"
                            + "|(authorization)\\s*[:=]\\s*(?!Bearer\\b)[^\\s,;]+");

    private LogSanitizer() {
    }

    public static String redact(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return "";
        }
        String sanitized = BEARER_PATTERN.matcher(rawMessage).replaceAll("Bearer [REDACTED]");
        sanitized = JWT_PATTERN.matcher(sanitized).replaceAll("[REDACTED_JWT]");
        sanitized =
                KEY_VALUE_SECRET_PATTERN
                        .matcher(sanitized)
                        .replaceAll(
                                match -> {
                                    String key = match.group(1) != null ? match.group(1) : match.group(2);
                                    return key + "=[REDACTED]";
                                });
        sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll("[REDACTED]@$1");
        return sanitized;
    }
}
