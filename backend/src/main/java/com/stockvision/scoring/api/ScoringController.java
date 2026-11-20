package com.stockvision.scoring.api;

import com.stockvision.scoring.application.OpportunitiesQuery;
import com.stockvision.scoring.application.ScoringApplicationService;
import com.stockvision.scoring.application.ScreenerQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for explainable 0–100 technical scoring and multi-instrument screening.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(
        name = "Scoring & Screener",
        description = "Explainable technical scores and filtered instrument screening")
public class ScoringController {

    private final ScoringApplicationService scoringApplicationService;

    public ScoringController(ScoringApplicationService scoringApplicationService) {
        this.scoringApplicationService = scoringApplicationService;
    }

    @GetMapping("/instruments/{symbol}/analysis")
    @Operation(
            summary = "Get explainable technical score",
            description =
                    "Computes and persists a versioned 0–100 technical score with weighted component breakdowns, "
                            + "UI-ready explanation, and explicit INSUFFICIENT_DATA handling.")
    @SecurityRequirement(name = "bearerAuth")
    public InstrumentAnalysisResponse analyzeInstrument(
            @PathVariable String symbol,
            @RequestParam(name = "interval", defaultValue = "1d") String interval,
            @RequestParam(name = "from", required = false) Instant from,
            @RequestParam(name = "to", required = false) Instant to,
            @RequestParam(name = "adjusted", defaultValue = "false") boolean adjusted) {
        return scoringApplicationService.analyzeInstrument(symbol, interval, from, to, adjusted);
    }

    @GetMapping("/screener")
    @Operation(
            summary = "Screen instruments by technical score and indicators",
            description =
                    "Filters, sorts, and paginates active exchange instruments by scope (BIST30, BIST100, SYNCED, ALL), "
                            + "optional search query, technical score, RSI, and trend direction.")
    @SecurityRequirement(name = "bearerAuth")
    public ScreenerPageResponse screen(
            @RequestParam(name = "exchange", defaultValue = "BIST") String exchange,
            @RequestParam(name = "interval", defaultValue = "1d") String interval,
            @RequestParam(name = "scope", defaultValue = "BIST30") String scope,
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "minScore", required = false) BigDecimal minScore,
            @RequestParam(name = "maxScore", required = false) BigDecimal maxScore,
            @RequestParam(name = "rsiMin", required = false) BigDecimal rsiMin,
            @RequestParam(name = "rsiMax", required = false) BigDecimal rsiMax,
            @RequestParam(name = "trend", required = false) String trend,
            @RequestParam(name = "includeInsufficientData", defaultValue = "false")
                    boolean includeInsufficientData,
            @RequestParam(name = "sortBy", defaultValue = "score") String sortBy,
            @RequestParam(name = "sortDir", defaultValue = "desc") String sortDir,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return scoringApplicationService.screen(
                new ScreenerQuery(
                        exchange,
                        interval,
                        scope,
                        query,
                        minScore,
                        maxScore,
                        rsiMin,
                        rsiMax,
                        trend,
                        includeInsufficientData,
                        sortBy,
                        sortDir,
                        page,
                        size));
    }

    @GetMapping("/instruments/{symbol}/setups")
    @Operation(
            summary = "Get rule-based technical levels, BIST margin reference, and risk/reward metrics",
            description =
                    "Computes mathematical support/watch zone, breakout trigger, resistance/realization "
                            + "zone, invalidation level, risk/reward multiples, and BIST ±10% theoretical "
                            + "daily margin reference with explicit INSUFFICIENT_DATA handling.")
    @SecurityRequirement(name = "bearerAuth")
    public InstrumentSetupResponse analyzeInstrumentSetup(
            @PathVariable String symbol,
            @RequestParam(name = "interval", defaultValue = "1d") String interval,
            @RequestParam(name = "from", required = false) Instant from,
            @RequestParam(name = "to", required = false) Instant to,
            @RequestParam(name = "adjusted", defaultValue = "false") boolean adjusted) {
        return scoringApplicationService.analyzeInstrumentSetup(
                symbol, interval, from, to, adjusted);
    }

    @GetMapping("/opportunities")
    @Operation(
            summary = "List rule-based momentum and technical setup candidates",
            description =
                    "Evaluates exchange instruments within the selected scope (BIST30, BIST100, SYNCED, ALL) "
                            + "or search query into descriptive momentum/breakout, pullback, overextended-risk, "
                            + "consolidation, and bearish setup profiles with ATR-derived reference zones and "
                            + "BIST ±10% theoretical margin context.")
    @SecurityRequirement(name = "bearerAuth")
    public OpportunitiesPageResponse listOpportunities(
            @RequestParam(name = "exchange", defaultValue = "BIST") String exchange,
            @RequestParam(name = "interval", defaultValue = "1d") String interval,
            @RequestParam(name = "scope", defaultValue = "BIST30") String scope,
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "minMomentumScore", required = false) BigDecimal minMomentumScore,
            @RequestParam(name = "includeInsufficientData", defaultValue = "true")
                    boolean includeInsufficientData,
            @RequestParam(name = "sortBy", defaultValue = "momentum") String sortBy,
            @RequestParam(name = "sortDir", defaultValue = "desc") String sortDir,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return scoringApplicationService.listOpportunities(
                new OpportunitiesQuery(
                        exchange,
                        interval,
                        scope,
                        query,
                        category,
                        minMomentumScore,
                        includeInsufficientData,
                        sortBy,
                        sortDir,
                        page,
                        size));
    }
}
