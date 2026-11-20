/**
 * Optional free AI explanation module (Phase 8).
 *
 * <p>Implements fact-only natural-language explanations behind {@link
 * com.stockvision.ai.domain.AiExplanationProvider}, supporting deterministic templates (default),
 * local open-source Ollama, or user-supplied free-tier Gemini credentials via environment variables.
 * Enforces strict JSON schema &amp; non-advice guardrails with automatic deterministic fallback and
 * full audit persistence in {@code ai_explanations}.
 */
package com.stockvision.ai;