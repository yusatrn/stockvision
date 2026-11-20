package com.stockvision.scoring.domain;

import java.math.BigDecimal;

/**
 * Descriptive technical posture derived strictly from mathematical indicator alignment.
 * Never represents a guaranteed buy or sell recommendation.
 */
public enum ScoreRegime {
    STRONG_BULLISH_TECHNICAL,
    MODERATE_BULLISH_TECHNICAL,
    NEUTRAL_TECHNICAL,
    MODERATE_BEARISH_TECHNICAL,
    STRONG_BEARISH_TECHNICAL,
    INSUFFICIENT_DATA;

    private static final BigDecimal STRONG_BULLISH_MIN = new BigDecimal("75.00");
    private static final BigDecimal MODERATE_BULLISH_MIN = new BigDecimal("60.00");
    private static final BigDecimal NEUTRAL_MIN = new BigDecimal("40.00");
    private static final BigDecimal MODERATE_BEARISH_MIN = new BigDecimal("25.00");

    public static ScoreRegime fromScore(BigDecimal score) {
        if (score == null) {
            return INSUFFICIENT_DATA;
        }
        if (score.compareTo(STRONG_BULLISH_MIN) >= 0) {
            return STRONG_BULLISH_TECHNICAL;
        }
        if (score.compareTo(MODERATE_BULLISH_MIN) >= 0) {
            return MODERATE_BULLISH_TECHNICAL;
        }
        if (score.compareTo(NEUTRAL_MIN) >= 0) {
            return NEUTRAL_TECHNICAL;
        }
        if (score.compareTo(MODERATE_BEARISH_MIN) >= 0) {
            return MODERATE_BEARISH_TECHNICAL;
        }
        return STRONG_BEARISH_TECHNICAL;
    }
}
