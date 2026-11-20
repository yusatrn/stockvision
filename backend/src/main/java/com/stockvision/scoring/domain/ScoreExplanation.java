package com.stockvision.scoring.domain;

import java.util.List;
import java.util.Objects;

/**
 * UI-ready structured explanation summarizing technical score drivers, data caveats, and limits.
 */
public record ScoreExplanation(
        String headline,
        List<String> keyDrivers,
        List<String> riskAndDataNotes,
        String methodologySummary,
        String disclaimer) {

    public ScoreExplanation {
        Objects.requireNonNull(headline, "headline must not be null");
        Objects.requireNonNull(keyDrivers, "keyDrivers must not be null");
        Objects.requireNonNull(riskAndDataNotes, "riskAndDataNotes must not be null");
        Objects.requireNonNull(methodologySummary, "methodologySummary must not be null");
        Objects.requireNonNull(disclaimer, "disclaimer must not be null");
        keyDrivers = List.copyOf(keyDrivers);
        riskAndDataNotes = List.copyOf(riskAndDataNotes);
    }
}
