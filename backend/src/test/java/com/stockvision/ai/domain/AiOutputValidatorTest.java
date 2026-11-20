package com.stockvision.ai.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AiOutputValidatorTest {

    private final AiOutputValidator validator = new AiOutputValidator(new ObjectMapper());

    @Test
    void acceptsValidJsonIncludingMarkdownCodeFence() {
        String fenced =
                """
                ```json
                {
                  "summary": "THYAO recorded a balanced bullish technical score of 72.50 on 1d simulated bars.",
                  "observedFacts": [
                    "Latest simulated close is 298.50 TRY with RSI(14) at 58.40.",
                    "Composite score is 72.50/100 via score-engine-v1.0.0."
                  ],
                  "interpretation": [
                    "Short-term trend and momentum readings are above neutral."
                  ],
                  "uncertaintiesAndRisks": [
                    "Underlying data is simulated and excludes company fundamentals and news."
                  ],
                  "confidenceNote": "Moderate confidence bound to deterministic simulated indicators."
                }
                ```
                """;

        AiOutputValidationResult result = validator.validate(fenced);
        assertThat(result.valid()).isTrue();
        assertThat(result.explanation()).isNotNull();
        assertThat(result.explanation().observedFacts()).hasSize(2);
    }

    @Test
    void rejectsMalformedJsonAndSchemaViolations() {
        AiOutputValidationResult malformed = validator.validate("{not-valid-json");
        assertThat(malformed.valid()).isFalse();
        assertThat(malformed.rejectionReason()).isEqualTo(AiFallbackReason.MALFORMED_OUTPUT);

        String missingSections =
                """
                {
                  "summary": "Valid length summary text here for test.",
                  "observedFacts": [],
                  "interpretation": ["One item"],
                  "uncertaintiesAndRisks": ["One risk"],
                  "confidenceNote": "Sufficient length note"
                }
                """;
        AiOutputValidationResult schemaInvalid = validator.validate(missingSections);
        assertThat(schemaInvalid.valid()).isFalse();
        assertThat(schemaInvalid.rejectionReason())
                .isEqualTo(AiFallbackReason.SCHEMA_VALIDATION_FAILED);
    }

    @Test
    void rejectsInvestmentAdviceAndProfitGuaranteesViaGuardrails() {
        String advisory =
                """
                {
                  "summary": "THYAO technical readings show strong upside with guaranteed profit potential.",
                  "observedFacts": ["RSI is 61.00 on 1d simulated bars."],
                  "interpretation": ["Momentum aligns upward."],
                  "uncertaintiesAndRisks": ["Simulated feed notice."],
                  "confidenceNote": "High deterministic indicator alignment."
                }
                """;

        AiOutputValidationResult blocked = validator.validate(advisory);
        assertThat(blocked.valid()).isFalse();
        assertThat(blocked.rejectionReason()).isEqualTo(AiFallbackReason.GUARDRAIL_VIOLATION);
    }
}
