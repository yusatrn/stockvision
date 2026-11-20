# Phase 4 — Technical Analysis Engine

Implement deterministic technical indicator calculations.

Indicators:
- SMA, EMA
- RSI (document chosen period and smoothing convention)
- MACD (fast, slow, signal periods)
- Bollinger Bands
- Stochastic
- Average True Range (ATR)
- Basic rolling volume average

Requirements:
- Use TA4J if it fits the selected versions and requirements; otherwise implement behind a clean interface and explain the decision.
- Define warm-up periods and insufficient-data behavior.
- Ensure bars are ordered and duplicate-free before calculation.
- No look-ahead bias: each output timestamp may use only data available up to that bar.
- Handle null/missing values explicitly.
- Unit-test against hand-calculated fixtures and edge cases (flat price, zero volume, short history).
- Expose indicator API with period/configuration metadata.
- Persist calculation version/config where analysis runs are stored.

Run tests and include a concise methodology note.
