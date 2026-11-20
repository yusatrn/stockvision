package com.stockvision.market.api;

import com.stockvision.market.application.MarketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for supported exchanges and market summary overviews.
 */
@RestController
@RequestMapping("/api/v1/markets")
@Tag(name = "Markets", description = "Exchanges, trading sessions, and market summaries")
public class MarketController {

    private final MarketService marketService;

    public MarketController(MarketService marketService) {
        this.marketService = marketService;
    }

    @GetMapping
    @Operation(
            summary = "List active exchanges",
            description =
                    "Returns configured exchanges with currency, timezone, and local trading session hours.")
    @SecurityRequirement(name = "bearerAuth")
    public List<ExchangeResponse> listMarkets() {
        return marketService.listMarkets();
    }

    @GetMapping("/summary")
    @Operation(
            summary = "Get market summary",
            description =
                    "Returns exchange summary, instrument count, data source/freshness metadata, and movers.")
    @SecurityRequirement(name = "bearerAuth")
    public MarketSummaryResponse getMarketSummary(
            @RequestParam(name = "exchange", defaultValue = "BIST") String exchange) {
        return marketService.getSummary(exchange);
    }
}
