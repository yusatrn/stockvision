package com.stockvision.technicalanalysis.infrastructure;

import com.stockvision.technicalanalysis.domain.DeterministicTechnicalIndicatorEngine;
import com.stockvision.technicalanalysis.domain.TechnicalIndicatorService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the deterministic technical indicator calculation engine bean.
 */
@Configuration
public class TechnicalAnalysisConfig {

    @Bean
    public TechnicalIndicatorService technicalIndicatorService() {
        return new DeterministicTechnicalIndicatorEngine();
    }
}
