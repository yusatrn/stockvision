package com.stockvision.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class CorsPropertiesTest {

    @Test
    void rejectsWildcardOrigins() {
        CorsProperties wildcard = new CorsProperties();
        wildcard.setAllowedOrigins(List.of("*"));

        assertThatThrownBy(wildcard::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Wildcard");

        CorsProperties partialWildcard = new CorsProperties();
        partialWildcard.setAllowedOrigins(List.of("https://*.stockvision.local"));

        assertThatThrownBy(partialWildcard::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Wildcard");
    }

    @Test
    void acceptsExplicitOriginsAndFiltersBlankEntries() {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOrigins(
                List.of("  ", " http://localhost:3000 ", "", "https://app.stockvision.local"));

        assertThatNoException().isThrownBy(properties::validate);
        assertThat(properties.getSanitizedAllowedOrigins())
                .containsExactly("http://localhost:3000", "https://app.stockvision.local");
    }
}
