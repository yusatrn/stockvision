package com.stockvision.alert.domain;

import com.stockvision.technicalanalysis.domain.IndicatorType;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps supported {@link IndicatorType} series to valid numeric output keys and default fields.
 */
public final class AlertIndicatorFieldCatalog {

    private static final Map<IndicatorType, List<String>> ALLOWED_FIELDS =
            Map.of(
                    IndicatorType.SMA, List.of("value"),
                    IndicatorType.EMA, List.of("value"),
                    IndicatorType.RSI, List.of("value"),
                    IndicatorType.ATR, List.of("value"),
                    IndicatorType.MACD, List.of("macd", "signal", "histogram"),
                    IndicatorType.BOLLINGER, List.of("upper", "middle", "lower", "bandwidth"),
                    IndicatorType.STOCHASTIC, List.of("k", "d"),
                    IndicatorType.VOLUME_AVG, List.of("average", "ratio"));

    private AlertIndicatorFieldCatalog() {
    }

    public static String resolveAndValidateField(IndicatorType indicatorType, String rawField) {
        if (indicatorType == null) {
            throw new IllegalArgumentException("indicatorType is required for indicator alerts");
        }
        List<String> supported = ALLOWED_FIELDS.get(indicatorType);
        if (supported == null || supported.isEmpty()) {
            throw new IllegalArgumentException("Unsupported indicatorType: " + indicatorType);
        }
        if (rawField == null || rawField.isBlank()) {
            return supported.get(0);
        }
        String normalized = rawField.trim().toLowerCase(Locale.ROOT);
        if (!supported.contains(normalized)) {
            throw new IllegalArgumentException(
                    "Invalid indicatorField '"
                            + rawField
                            + "' for "
                            + indicatorType
                            + ". Allowed: "
                            + String.join(", ", supported));
        }
        return normalized;
    }

    public static List<String> allowedFields(IndicatorType indicatorType) {
        return ALLOWED_FIELDS.getOrDefault(indicatorType, List.of());
    }
}
