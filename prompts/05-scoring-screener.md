# Phase 5 — Explainable Scoring and Screener

Implement a configurable technical score from 0 to 100.

Initial weights:
- EMA/SMA trend 20%
- RSI 15%
- MACD 15%
- Volume 15%
- Bollinger 10%
- Momentum 10%
- Support/resistance 15%

Requirements:
- Each component returns normalized contribution, weight, explanation and data sufficiency status.
- Final score is weighted and reproducible; weights must sum to 100%.
- Store algorithm version, parameters, source data range, calculation timestamp and provider.
- Missing or stale inputs must not silently become neutral; return `INSUFFICIENT_DATA` or explicitly configured handling.
- Do not label scores as guaranteed buy/sell recommendations.
- Screener supports filters and pagination.
- Tests cover weight normalization, missing data, boundary conditions and deterministic repeatability.
- Add UI-ready explanation DTO.

Document limitations: technical indicators are descriptive and do not predict returns reliably.
