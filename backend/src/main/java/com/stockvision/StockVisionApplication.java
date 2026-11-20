package com.stockvision;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the StockVision AI modular monolith backend.
 *
 * <p>JPA auditing is configured in {@code com.stockvision.common.config.JpaAuditingConfig} so that
 * {@code @WebMvcTest} slices remain decoupled from JPA metamodel initialization.
 */
@SpringBootApplication
public class StockVisionApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(StockVisionApplication.class, args);
    }
}
