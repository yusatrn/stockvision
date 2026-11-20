package com.stockvision.alert.domain;

/**
 * Supported market condition comparisons for user alert rules.
 */
public enum AlertConditionType {
    PRICE_ABOVE,
    PRICE_BELOW,
    INDICATOR_ABOVE,
    INDICATOR_BELOW;

    public boolean isIndicatorCondition() {
        return this == INDICATOR_ABOVE || this == INDICATOR_BELOW;
    }

    public boolean isAbove() {
        return this == PRICE_ABOVE || this == INDICATOR_ABOVE;
    }
}
