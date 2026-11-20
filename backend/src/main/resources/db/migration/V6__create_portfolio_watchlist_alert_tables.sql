-- StockVision AI — Phase 7 Watchlist, Portfolio & Alert Tables
-- Stores user-owned watchlists, virtual portfolio transaction ledgers, and idempotent alert rules/notifications in UTC.

CREATE TABLE IF NOT EXISTS watchlists (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(80) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_watchlists_user_name_ci
    ON watchlists (user_id, LOWER(name));

CREATE INDEX IF NOT EXISTS idx_watchlists_user_created
    ON watchlists (user_id, created_at ASC);

CREATE TABLE IF NOT EXISTS watchlist_items (
    id UUID PRIMARY KEY,
    watchlist_id UUID NOT NULL REFERENCES watchlists(id) ON DELETE CASCADE,
    instrument_id UUID NOT NULL REFERENCES instruments(id),
    notes VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_watchlist_items_list_instrument UNIQUE (watchlist_id, instrument_id)
);

CREATE INDEX IF NOT EXISTS idx_watchlist_items_watchlist
    ON watchlist_items (watchlist_id, created_at ASC);

CREATE TABLE IF NOT EXISTS portfolios (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(80) NOT NULL,
    description VARCHAR(255),
    base_currency VARCHAR(8) NOT NULL DEFAULT 'TRY',
    fee_tax_policy VARCHAR(32) NOT NULL DEFAULT 'EXCLUDED_MVP'
        CHECK (fee_tax_policy IN ('EXCLUDED_MVP', 'EXPLICIT_PER_TXN')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_portfolios_user_name_ci
    ON portfolios (user_id, LOWER(name));

CREATE INDEX IF NOT EXISTS idx_portfolios_user_created
    ON portfolios (user_id, created_at ASC);

CREATE TABLE IF NOT EXISTS portfolio_transactions (
    id UUID PRIMARY KEY,
    portfolio_id UUID NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE,
    instrument_id UUID NOT NULL REFERENCES instruments(id),
    side VARCHAR(8) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity NUMERIC(19, 6) NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(19, 4) NOT NULL CHECK (unit_price > 0),
    fee_amount NUMERIC(19, 2) NOT NULL DEFAULT 0.00 CHECK (fee_amount >= 0),
    tax_amount NUMERIC(19, 2) NOT NULL DEFAULT 0.00 CHECK (tax_amount >= 0),
    currency VARCHAR(8) NOT NULL DEFAULT 'TRY',
    executed_at TIMESTAMPTZ NOT NULL,
    notes VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_portfolio_txns_portfolio_exec
    ON portfolio_transactions (portfolio_id, executed_at ASC, created_at ASC, id ASC);

CREATE TABLE IF NOT EXISTS alert_rules (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    instrument_id UUID NOT NULL REFERENCES instruments(id),
    condition_type VARCHAR(32) NOT NULL
        CHECK (condition_type IN ('PRICE_ABOVE', 'PRICE_BELOW', 'INDICATOR_ABOVE', 'INDICATOR_BELOW')),
    indicator_type VARCHAR(32)
        CHECK (indicator_type IS NULL OR indicator_type IN ('SMA', 'EMA', 'RSI', 'MACD', 'BOLLINGER', 'STOCHASTIC', 'ATR', 'VOLUME_AVG')),
    indicator_field VARCHAR(32),
    bar_interval VARCHAR(16) NOT NULL DEFAULT '1d'
        CHECK (bar_interval IN ('1d', '1h', '15m')),
    threshold_value NUMERIC(19, 6) NOT NULL,
    cooldown_minutes INT NOT NULL DEFAULT 60
        CHECK (cooldown_minutes >= 1 AND cooldown_minutes <= 43200),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'PAUSED')),
    trigger_state VARCHAR(16) NOT NULL DEFAULT 'ARMED'
        CHECK (trigger_state IN ('ARMED', 'TRIGGERED')),
    last_evaluated_at TIMESTAMPTZ,
    last_triggered_at TIMESTAMPTZ,
    last_observed_value NUMERIC(19, 6),
    note VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_alert_rules_user
    ON alert_rules (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_alert_rules_active
    ON alert_rules (status, instrument_id);

CREATE TABLE IF NOT EXISTS alert_notifications (
    id UUID PRIMARY KEY,
    alert_rule_id UUID NOT NULL REFERENCES alert_rules(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    instrument_id UUID NOT NULL REFERENCES instruments(id),
    symbol VARCHAR(24) NOT NULL,
    condition_type VARCHAR(32) NOT NULL,
    indicator_type VARCHAR(32),
    indicator_field VARCHAR(32),
    threshold_value NUMERIC(19, 6) NOT NULL,
    observed_value NUMERIC(19, 6) NOT NULL,
    data_timestamp TIMESTAMPTZ NOT NULL,
    provider_id VARCHAR(64) NOT NULL,
    provider_display_name VARCHAR(120) NOT NULL,
    provider_mode VARCHAR(24) NOT NULL,
    simulated BOOLEAN NOT NULL DEFAULT TRUE,
    freshness_status VARCHAR(24) NOT NULL,
    stale_data BOOLEAN NOT NULL DEFAULT FALSE,
    message TEXT NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_alert_notifications_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_alert_notifications_user_created
    ON alert_notifications (user_id, created_at DESC);
