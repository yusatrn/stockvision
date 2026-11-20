# Phase 7 — Portfolio, Watchlist and Alerts

Implement user-owned watchlists, virtual portfolios and alerts.

Portfolio:
- Virtual portfolios and transaction ledger.
- Buy/sell transactions with BigDecimal quantities and prices.
- Derive holdings and average cost from transactions; do not store inconsistent duplicated totals.
- Realized/unrealized P&L clearly distinguished.
- Define fee/tax treatment as configurable or explicitly excluded in MVP.
- Enforce ownership checks on every endpoint.

Watchlist:
- Multiple named lists per user.
- Prevent duplicate instrument entries within a list.

Alerts:
- Price above/below threshold and indicator threshold conditions.
- Evaluate rules via scheduled job with idempotent notification creation.
- Avoid repeated notification storms; implement cooldown/trigger state.
- Clearly indicate data freshness and provider source.

Add migrations, APIs, UI screens and tests for authorization, calculations and duplicate events.
