package com.stockvision.ai.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Validates raw LLM text against a strict JSON schema and blocks prohibited investment-advice or
 * guaranteed-profit claims prior to persistence or UI rendering.
 */
public class AiOutputValidator {

    private static final List<String> FORBIDDEN_PHRASES =
            List.of(
                    "guaranteed profit",
                    "risk-free",
                    "risk free return",
                    "100% certain",
                    "definitely will rise",
                    "sure thing",
                    "buy immediately",
                    "sell immediately",
                    "strong buy recommendation",
                    "kesin kazanç",
                    "garanti kâr",
                    "garanti getiri",
                    "risksiz getiri",
                    "hemen al",
                    "hemen sat");

    private final ObjectMapper objectMapper;

    public AiOutputValidator(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public AiOutputValidationResult validate(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return AiOutputValidationResult.rejected(
                    AiFallbackReason.MALFORMED_OUTPUT, "Provider returned empty response");
        }

        String normalizedJson = stripMarkdownFence(rawText);
        JsonNode root;
        try {
            root = objectMapper.readTree(normalizedJson);
        } catch (JsonProcessingException ex) {
            return AiOutputValidationResult.rejected(
                    AiFallbackReason.MALFORMED_OUTPUT, "Provider response is not valid JSON");
        }

        if (root == null || !root.isObject()) {
            return AiOutputValidationResult.rejected(
                    AiFallbackReason.SCHEMA_VALIDATION_FAILED, "Root JSON node must be an object");
        }

        String summary = readBoundedText(root.get("summary"), 15, 800);
        if (summary == null) {
            return AiOutputValidationResult.rejected(
                    AiFallbackReason.SCHEMA_VALIDATION_FAILED,
                    "summary must be a non-blank string between 15 and 800 chars");
        }

        List<String> observedFacts = readStringList(root.get("observedFacts"), 1, 10, 320);
        if (observedFacts == null) {
            return AiOutputValidationResult.rejected(
                    AiFallbackReason.SCHEMA_VALIDATION_FAILED,
                    "observedFacts must contain 1 to 10 concise items");
        }

        List<String> interpretation = readStringList(root.get("interpretation"), 1, 8, 320);
        if (interpretation == null) {
            return AiOutputValidationResult.rejected(
                    AiFallbackReason.SCHEMA_VALIDATION_FAILED,
                    "interpretation must contain 1 to 8 concise items");
        }

        List<String> uncertaintiesAndRisks =
                readStringList(root.get("uncertaintiesAndRisks"), 1, 8, 320);
        if (uncertaintiesAndRisks == null) {
            return AiOutputValidationResult.rejected(
                    AiFallbackReason.SCHEMA_VALIDATION_FAILED,
                    "uncertaintiesAndRisks must contain 1 to 8 concise items");
        }

        String confidenceNote = readBoundedText(root.get("confidenceNote"), 10, 400);
        if (confidenceNote == null) {
            return AiOutputValidationResult.rejected(
                    AiFallbackReason.SCHEMA_VALIDATION_FAILED,
                    "confidenceNote must be a non-blank string between 10 and 400 chars");
        }

        StructuredAiExplanation structured =
                new StructuredAiExplanation(
                        summary,
                        observedFacts,
                        interpretation,
                        uncertaintiesAndRisks,
                        confidenceNote);

        String combinedLower = buildCombinedLowercase(structured);
        for (String forbidden : FORBIDDEN_PHRASES) {
            if (combinedLower.contains(forbidden)) {
                return AiOutputValidationResult.rejected(
                        AiFallbackReason.GUARDRAIL_VIOLATION,
                        "Response contained prohibited advisory/guarantee phrase: " + forbidden);
            }
        }

        return AiOutputValidationResult.accepted(structured);
    }

    private static String stripMarkdownFence(String rawText) {
        String trimmed = rawText.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewLine = trimmed.indexOf('\n');
        if (firstNewLine < 0) {
            return trimmed;
        }
        String withoutOpening = trimmed.substring(firstNewLine + 1);
        int closingFence = withoutOpening.lastIndexOf("```");
        if (closingFence >= 0) {
            withoutOpening = withoutOpening.substring(0, closingFence);
        }
        return withoutOpening.trim();
    }

    private static String readBoundedText(JsonNode node, int minLen, int maxLen) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        String val = node.asText().trim();
        if (val.length() < minLen || val.length() > maxLen) {
            return null;
        }
        return val;
    }

    private static List<String> readStringList(
            JsonNode node, int minItems, int maxItems, int maxItemLength) {
        if (node == null || !node.isArray()) {
            return null;
        }
        if (node.size() < minItems || node.size() > maxItems) {
            return null;
        }
        List<String> items = new ArrayList<>(node.size());
        for (JsonNode child : node) {
            if (child == null || !child.isTextual()) {
                return null;
            }
            String text = child.asText().trim();
            if (text.isEmpty() || text.length() > maxItemLength) {
                return null;
            }
            items.add(text);
        }
        return items;
    }

    private static String buildCombinedLowercase(StructuredAiExplanation s) {
        StringBuilder sb = new StringBuilder();
        sb.append(s.summary()).append(' ');
        s.observedFacts().forEach(item -> sb.append(item).append(' '));
        s.interpretation().forEach(item -> sb.append(item).append(' '));
        s.uncertaintiesAndRisks().forEach(item -> sb.append(item).append(' '));
        sb.append(s.confidenceNote());
        return sb.toString().toLowerCase(Locale.ROOT);
    }
}
