# Phase 8 — AI Analysis Explanations

Add optional AI-generated natural-language explanations, not autonomous trading.

Requirements:
- AI provider behind an interface; credentials from environment.
- AI receives only structured, persisted analysis facts and market-data metadata.
- Prompt must prohibit invented prices, indicators, news or fundamentals.
- Require the model to distinguish observed facts, interpretation and uncertainty.
- Validate structured output against a schema before storing/displaying.
- If AI provider is unavailable, show deterministic template-based explanation.
- Store model/provider, prompt version, analysis-run ID and generation timestamp.
- Do not send personal portfolio data to external AI unless explicitly required and consented.
- Display disclaimer that generated text is informational, may be wrong and is not investment advice.
- Tests for malformed model output, missing facts, provider timeout and fallback behavior.
