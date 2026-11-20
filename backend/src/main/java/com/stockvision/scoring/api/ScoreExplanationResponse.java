package com.stockvision.scoring.api;

import com.stockvision.scoring.domain.ScoreExplanation;
import java.util.List;

/**
 * UI-ready explanation DTO detailing technical drivers, caveats, methodology, and disclaimer.
 */
public record ScoreExplanationResponse(
        String headline,
        List<String> keyDrivers,
        List<String> riskAndDataNotes,
        String methodologySummary,
        String disclaimer) {

    public static ScoreExplanationResponse from(ScoreExplanation explanation) {
        return new ScoreExplanationResponse(
                explanation.headline(),
                explanation.keyDrivers(),
                explanation.riskAndDataNotes(),
                explanation.methodologySummary(),
                explanation.disclaimer());
    }
}
