package com.stockvision.technicalanalysis.api;

import com.stockvision.common.exception.AppException;
import com.stockvision.common.exception.ErrorCode;
import com.stockvision.technicalanalysis.application.TechnicalAnalysisApplicationService;
import com.stockvision.technicalanalysis.application.TechnicalIndicatorsQuery;
import com.stockvision.technicalanalysis.domain.IndicatorConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for deterministic technical indicator computations.
 */
@RestController
@RequestMapping("/api/v1/instruments")
@Tag(
        name = "Technical Analysis",
        description = "Deterministic technical indicator calculation endpoints")
public class TechnicalAnalysisController {

    private final TechnicalAnalysisApplicationService applicationService;

    public TechnicalAnalysisController(TechnicalAnalysisApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping("/{symbol}/indicators")
    @Operation(
            summary = "Calculate technical indicators",
            description =
                    "Calculates deterministic technical indicators over ordered UTC OHLCV bars without look-ahead bias, "
                            + "exposes warm-up and INSUFFICIENT_DATA statuses, and persists calculation metadata.")
    @SecurityRequirement(name = "bearerAuth")
    public TechnicalIndicatorsResponse getIndicators(
            @PathVariable String symbol,
            @RequestParam(name = "interval", defaultValue = "1d") String interval,
            @RequestParam(name = "indicators", required = false) List<String> indicators,
            @RequestParam(name = "from", required = false) Instant from,
            @RequestParam(name = "to", required = false) Instant to,
            @RequestParam(name = "adjusted", defaultValue = "false") boolean adjusted,
            @RequestParam(name = "smaPeriod", required = false) Integer smaPeriod,
            @RequestParam(name = "emaPeriod", required = false) Integer emaPeriod,
            @RequestParam(name = "rsiPeriod", required = false) Integer rsiPeriod,
            @RequestParam(name = "macdFast", required = false) Integer macdFast,
            @RequestParam(name = "macdSlow", required = false) Integer macdSlow,
            @RequestParam(name = "macdSignal", required = false) Integer macdSignal,
            @RequestParam(name = "bbPeriod", required = false) Integer bbPeriod,
            @RequestParam(name = "bbStdDev", required = false) BigDecimal bbStdDev,
            @RequestParam(name = "stochK", required = false) Integer stochK,
            @RequestParam(name = "stochD", required = false) Integer stochD,
            @RequestParam(name = "atrPeriod", required = false) Integer atrPeriod,
            @RequestParam(name = "volumeAvgPeriod", required = false) Integer volumeAvgPeriod) {
        IndicatorConfiguration configuration =
                buildConfiguration(
                        smaPeriod,
                        emaPeriod,
                        rsiPeriod,
                        macdFast,
                        macdSlow,
                        macdSignal,
                        bbPeriod,
                        bbStdDev,
                        stochK,
                        stochD,
                        atrPeriod,
                        volumeAvgPeriod);

        return applicationService.calculateIndicators(
                new TechnicalIndicatorsQuery(
                        symbol, interval, indicators, from, to, adjusted, configuration));
    }

    private static IndicatorConfiguration buildConfiguration(
            Integer smaPeriod,
            Integer emaPeriod,
            Integer rsiPeriod,
            Integer macdFast,
            Integer macdSlow,
            Integer macdSignal,
            Integer bbPeriod,
            BigDecimal bbStdDev,
            Integer stochK,
            Integer stochD,
            Integer atrPeriod,
            Integer volumeAvgPeriod) {
        IndicatorConfiguration defaults = IndicatorConfiguration.defaults();
        try {
            return new IndicatorConfiguration(
                    smaPeriod != null ? smaPeriod : defaults.smaPeriod(),
                    emaPeriod != null ? emaPeriod : defaults.emaPeriod(),
                    rsiPeriod != null ? rsiPeriod : defaults.rsiPeriod(),
                    macdFast != null ? macdFast : defaults.macdFastPeriod(),
                    macdSlow != null ? macdSlow : defaults.macdSlowPeriod(),
                    macdSignal != null ? macdSignal : defaults.macdSignalPeriod(),
                    bbPeriod != null ? bbPeriod : defaults.bollingerPeriod(),
                    bbStdDev != null ? bbStdDev : defaults.bollingerStdDevMultiplier(),
                    stochK != null ? stochK : defaults.stochasticKPeriod(),
                    stochD != null ? stochD : defaults.stochasticDPeriod(),
                    atrPeriod != null ? atrPeriod : defaults.atrPeriod(),
                    volumeAvgPeriod != null ? volumeAvgPeriod : defaults.volumeAvgPeriod());
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, ex.getMessage());
        }
    }
}
