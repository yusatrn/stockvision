package com.stockvision.ai.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockvision.ai.domain.AiExplanationProvider;
import com.stockvision.ai.domain.AiOutputValidator;
import com.stockvision.ai.domain.AiPromptBuilder;
import com.stockvision.ai.domain.DeterministicTemplateExplanationGenerator;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration selecting the configured free AI provider and deterministic fallback helpers.
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Bean
    public DeterministicTemplateExplanationGenerator deterministicTemplateExplanationGenerator() {
        return new DeterministicTemplateExplanationGenerator();
    }

    @Bean
    public AiPromptBuilder aiPromptBuilder(ObjectMapper objectMapper) {
        return new AiPromptBuilder(objectMapper);
    }

    @Bean
    public AiOutputValidator aiOutputValidator(ObjectMapper objectMapper) {
        return new AiOutputValidator(objectMapper);
    }

    @Bean
    public AiExplanationProvider aiExplanationProvider(
            AiProperties properties,
            DeterministicTemplateExplanationGenerator templateGenerator,
            ObjectMapper objectMapper) {
        String configured =
                properties.getProvider() != null
                        ? properties.getProvider().trim().toLowerCase(Locale.ROOT)
                        : "template";

        return switch (configured) {
            case "ollama" -> {
                log.info("Configured AI explanation provider: ollama (local free LLM)");
                yield new OllamaAiExplanationProvider(properties, objectMapper);
            }
            case "gemini-free" -> {
                log.info("Configured AI explanation provider: gemini-free (user free-tier key)");
                yield new GeminiFreeAiExplanationProvider(properties, objectMapper);
            }
            case "template", "deterministic-template", "" -> {
                log.info("Configured AI explanation provider: deterministic-template");
                yield new DeterministicTemplateAiProvider(templateGenerator, objectMapper);
            }
            default -> {
                log.warn(
                        "Unsupported app.ai.provider '{}'; falling back safely to deterministic-template",
                        configured);
                yield new DeterministicTemplateAiProvider(templateGenerator, objectMapper);
            }
        };
    }
}
