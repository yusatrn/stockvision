package com.stockvision;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Generates an ephemeral runtime-only HMAC key for tests so no static JWT secret is committed.
 */
public final class TestSecuritySupport {

    private static final String EPHEMERAL_TEST_JWT_SECRET = generateSecret();

    private TestSecuritySupport() {
    }

    public static String runtimeJwtSecret() {
        return EPHEMERAL_TEST_JWT_SECRET;
    }

    public static void registerJwtSecret(DynamicPropertyRegistry registry) {
        registry.add("app.security.jwt.secret", TestSecuritySupport::runtimeJwtSecret);
    }

    private static String generateSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
