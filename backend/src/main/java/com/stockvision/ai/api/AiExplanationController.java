package com.stockvision.ai.api;

import com.stockvision.ai.application.AiExplanationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated endpoints serving optional free-AI or deterministic template natural-language
 * explanations grounded solely in persisted quantitative analysis facts.
 */
@RestController
@RequestMapping("/api/v1/instruments/{symbol}/ai-explanation")
@Tag(
        name = "AI Explanations",
        description = "Fact-grounded optional AI and deterministic template explanations")
@SecurityRequirement(name = "bearerAuth")
public class AiExplanationController {

    private final AiExplanationService aiExplanationService;

    public AiExplanationController(AiExplanationService aiExplanationService) {
        this.aiExplanationService = aiExplanationService;
    }

    @GetMapping
    @Operation(
            summary = "Get or generate AI explanation",
            description =
                    "Returns a persisted natural-language explanation separating observed facts, interpretation, and uncertainties; falls back to deterministic template text whenever an external provider is unavailable.")
    public AiExplanationResponse getExplanation(
            @PathVariable("symbol") String symbol,
            @RequestParam(name = "interval", defaultValue = "1d") String interval,
            @RequestParam(name = "adjusted", defaultValue = "false") boolean adjusted,
            @RequestParam(name = "scoreRunId", required = false) UUID scoreRunId) {
        return aiExplanationService.getOrGenerateExplanation(
                symbol, interval, adjusted, scoreRunId, false);
    }

    @PostMapping("/regenerate")
    @Operation(
            summary = "Regenerate AI explanation",
            description =
                    "Bypasses cached explanations for the resolved score run and generates/persists a new auditable explanation record.")
    public AiExplanationResponse regenerateExplanation(
            @PathVariable("symbol") String symbol,
            @RequestParam(name = "interval", defaultValue = "1d") String interval,
            @RequestParam(name = "adjusted", defaultValue = "false") boolean adjusted,
            @RequestParam(name = "scoreRunId", required = false) UUID scoreRunId) {
        return aiExplanationService.getOrGenerateExplanation(
                symbol, interval, adjusted, scoreRunId, true);
    }
}
