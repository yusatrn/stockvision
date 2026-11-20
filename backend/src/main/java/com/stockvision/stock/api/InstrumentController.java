package com.stockvision.stock.api;

import com.stockvision.stock.application.InstrumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for searching tracked instruments and inspecting instrument metadata and quotes.
 */
@RestController
@RequestMapping("/api/v1/instruments")
@Tag(name = "Instruments", description = "Instrument catalog search and detail endpoints")
public class InstrumentController {

    private final InstrumentService instrumentService;

    public InstrumentController(InstrumentService instrumentService) {
        this.instrumentService = instrumentService;
    }

    @GetMapping
    @Operation(
            summary = "Search instruments",
            description =
                    "Searches active instruments by symbol or name with optional exchange filter and pagination.")
    @SecurityRequirement(name = "bearerAuth")
    public InstrumentPageResponse searchInstruments(
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "exchange", required = false) String exchange,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return instrumentService.search(query, exchange, page, size);
    }

    @GetMapping("/{symbol}")
    @Operation(
            summary = "Get instrument detail",
            description =
                    "Returns instrument metadata, exchange details, latest quote snapshot, data source, and freshness status.")
    @SecurityRequirement(name = "bearerAuth")
    public InstrumentDetailResponse getInstrumentDetail(@PathVariable String symbol) {
        return instrumentService.getDetail(symbol);
    }
}
