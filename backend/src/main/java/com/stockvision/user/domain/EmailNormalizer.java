package com.stockvision.user.domain;

import java.util.Locale;

/**
 * Canonicalizes user email addresses using {@link Locale#ROOT} so uniqueness checks and lookups
 * are locale-independent and case-insensitive.
 */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        if (email == null) {
            throw new IllegalArgumentException("email must not be null");
        }
        String trimmed = email.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}
