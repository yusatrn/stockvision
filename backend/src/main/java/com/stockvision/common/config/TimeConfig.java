package com.stockvision.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a shared UTC {@link Clock} bean so all timestamps are stored and emitted in UTC.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock utcClock() {
        return Clock.systemUTC();
    }
}
