package com.stockvision.stock.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Canonicalizes and validates ticker symbols using {@link Locale#ROOT}.
 */
public final class SymbolNormalizer {

    private static final Pattern VALID_SYMBOL = Pattern.compile("^[A-Z0-9._-]{1,24}$");

    private SymbolNormalizer() {
    }

    public static String normalize(String rawSymbol) {
        if (rawSymbol == null) {
            throw new IllegalArgumentException("symbol must not be null");
        }
        String normalized = rawSymbol.trim().toUpperCase(Locale.ROOT);
        if (!VALID_SYMBOL.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid instrument symbol format");
        }
        return normalized;
    }
}
