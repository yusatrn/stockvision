# Architecture

## High-level
Next.js web client -> Spring Boot REST API -> PostgreSQL / Redis -> MarketDataProvider adapter.

## Backend modules
- `auth`: registration, login, token lifecycle
- `user`: profile and preferences
- `market`: exchanges, trading sessions, market summaries
- `stock`: instruments, symbols, metadata
- `marketdata`: OHLCV ingestion, provider abstraction, freshness checks
- `technicalanalysis`: indicator calculation
- `fundamentals`: fundamental metrics (later phase)
- `scoring`: explainable technical score and score versioning
- `portfolio`: virtual portfolios and transactions
- `watchlist`: user watchlists
- `alert`: rule definition, evaluation and notifications
- `ai`: optional natural-language explanations grounded only in stored analysis
- `common`: errors, security utilities, auditing, shared primitives

## Key interfaces
`MarketDataProvider`
- `getHistoricalBars(symbol, interval, from, to)`
- `getLatestQuote(symbol)`
- `getProviderMetadata()`

`TechnicalIndicatorService`
- accepts ordered OHLCV bars and requested indicators
- returns values with timestamps and warm-up/insufficient-data metadata

## Data model outline
- users
- refresh_tokens (if refresh-token flow is implemented)
- exchanges
- instruments
- market_data_bars
- provider_sync_runs
- analysis_runs
- indicator_values (optional; can compute on demand initially)
- portfolios
- portfolio_transactions
- watchlists
- watchlist_items
- alert_rules
- notifications

Use composite uniqueness for `(instrument_id, interval, timestamp, provider)` where appropriate. Add indexes for symbol, timestamp, user ownership and common query filters.

## API conventions
- Base path `/api/v1`
- JSON request/response
- ISO-8601 timestamps
- Pagination for collection endpoints
- Bean Validation on inputs
- Consistent error response: timestamp, status, code, message, path, traceId
- OpenAPI documentation

## Data integrity
- Store provider, currency, interval, timezone assumptions and ingestion timestamp.
- Never silently mix adjusted and unadjusted prices.
- Handle market holidays and missing bars explicitly.
- Prevent duplicate bars with database constraints.
- Make ingestion idempotent.
