package com.stockvision.technicalanalysis.domain;

import com.stockvision.marketdata.domain.BarInterval;
import com.stockvision.marketdata.domain.OhlcvBar;
import java.util.List;
import java.util.Objects;

/**
 * Validates that input OHLCV bars are non-null, strictly ascending without duplicates, and
 * homogeneous in interval, provider, and price adjustment basis before indicator computation.
 */
public final class BarSeriesValidator {

    private BarSeriesValidator() {
    }

    public static void validate(List<OhlcvBar> orderedBars) {
        Objects.requireNonNull(orderedBars, "orderedBars must not be null");
        if (orderedBars.isEmpty()) {
            return;
        }

        OhlcvBar first = Objects.requireNonNull(orderedBars.getFirst(), "bar must not be null");
        BarInterval expectedInterval = first.interval();
        String expectedProvider = first.provider();
        boolean expectedAdjusted = first.adjusted();

        for (int i = 1; i < orderedBars.size(); i++) {
            OhlcvBar current = Objects.requireNonNull(orderedBars.get(i), "bar must not be null");
            OhlcvBar previous = orderedBars.get(i - 1);

            if (!current.timestamp().isAfter(previous.timestamp())) {
                throw new IllegalArgumentException(
                        "OHLCV bars must be strictly ascending and duplicate-free (violation at index "
                                + i
                                + ")");
            }
            if (current.interval() != expectedInterval
                    || !expectedProvider.equals(current.provider())
                    || current.adjusted() != expectedAdjusted) {
                throw new IllegalArgumentException(
                        "OHLCV bar series must share consistent interval, provider, and adjustment flag");
            }
        }
    }
}
