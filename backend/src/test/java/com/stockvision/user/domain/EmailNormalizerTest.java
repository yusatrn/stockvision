package com.stockvision.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class EmailNormalizerTest {

    @Test
    void trimsAndLowercasesUsingRootLocaleEvenUnderTurkishDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(EmailNormalizer.normalize("  INVESTOR.INFO@Example.COM  "))
                    .isEqualTo("investor.info@example.com");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void rejectsNullOrBlankEmail() {
        assertThatThrownBy(() -> EmailNormalizer.normalize(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EmailNormalizer.normalize("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
