package com.stockvision.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogSanitizerTest {

    @Test
    void redactsTokensSecretsAndPersonalEmails() {
        String raw =
                "Auth failure for Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.payloadpart12345.sigpart67890 "
                        + "email=analyst@example.com password=SuperSecret99 apiKey=AIzaFakeKey123 authorization=BasicSecret";

        String redacted = LogSanitizer.redact(raw);

        assertThat(redacted)
                .doesNotContain("eyJhbGciOiJIUzI1NiJ9")
                .doesNotContain("analyst@example.com")
                .doesNotContain("SuperSecret99")
                .doesNotContain("AIzaFakeKey123")
                .doesNotContain("BasicSecret")
                .contains("Bearer [REDACTED]")
                .contains("[REDACTED]@example.com")
                .contains("password=[REDACTED]")
                .contains("apiKey=[REDACTED]")
                .contains("authorization=[REDACTED]");
    }
}
