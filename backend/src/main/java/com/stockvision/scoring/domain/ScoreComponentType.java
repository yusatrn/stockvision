package com.stockvision.scoring.domain;

/**
 * Technical scoring components contributing to the composite 0–100 score.
 */
public enum ScoreComponentType {
    TREND,
    RSI,
    MACD,
    VOLUME,
    BOLLINGER,
    MOMENTUM,
    SUPPORT_RESISTANCE
}
