package com.stockvision.scoring.infrastructure;

import com.stockvision.scoring.domain.DeterministicTechnicalScoringEngine;
import com.stockvision.scoring.domain.DeterministicTechnicalSetupEngine;
import com.stockvision.scoring.domain.TechnicalScoringService;
import com.stockvision.scoring.domain.TechnicalSetupService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers scoring properties and deterministic technical scoring/setup engine beans.
 */
@Configuration
@EnableConfigurationProperties(ScoringProperties.class)
public class ScoringConfig {

    @Bean
    public TechnicalScoringService technicalScoringService() {
        return new DeterministicTechnicalScoringEngine();
    }

    @Bean
    public TechnicalSetupService technicalSetupService() {
        return new DeterministicTechnicalSetupEngine();
    }
}
