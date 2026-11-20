package com.stockvision.stock.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class SymbolNormalizerTest {

    @Test
    void trimsAndUppercasesUsingRootLocaleUnderTurkishLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(SymbolNormalizer.normalize("  sise  ")).isEqualTo("SISE");
            assertThat(SymbolNormalizer.normalize("thyao.e")).isEqualTo("THYAO.E");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void rejectsBlankOrInvalidSymbols() {
        assertThatThrownBy(() -> SymbolNormalizer.normalize(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SymbolNormalizer.normalize("   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SymbolNormalizer.normalize("THYAO/BAD"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
