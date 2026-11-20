# Phase 3 — Instruments and Market Data

Implement instruments and historical OHLCV data using a provider abstraction.

Requirements:
- Instrument and Exchange domain models.
- `MarketDataProvider` interface as described in architecture.
- Implement deterministic `MockMarketDataProvider`; clearly label all generated data as simulated.
- OHLCV model with timestamp, open, high, low, close, volume, currency, interval, provider and adjusted/unadjusted flag.
- Validate OHLCV invariants (high >= open/close/low, low <= open/close/high, volume >= 0).
- Idempotent ingestion and database uniqueness constraints.
- APIs for instrument search, instrument detail and historical bars.
- Pagination where applicable, date range limits, input validation.
- Add freshness metadata and provider sync status.
- Never imply simulated data is real market data.
- Tests for validation, duplicate ingestion, provider failures and API behavior.

Update OpenAPI and README. Run all tests.
