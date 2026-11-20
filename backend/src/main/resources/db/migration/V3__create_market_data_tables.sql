-- StockVision AI — Phase 3 Exchanges, Instruments, Market Data Bars & Sync Run Audit Tables
-- All timestamps stored in UTC (TIMESTAMPTZ). All OHLCV prices use NUMERIC(19, 4) with DB invariant checks.
-- Reference catalog entries only; no market prices are seeded in SQL.

CREATE TABLE IF NOT EXISTS exchanges (
    id UUID PRIMARY KEY,
    code VARCHAR(16) NOT NULL,
    name VARCHAR(120) NOT NULL,
    country_code VARCHAR(2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    session_open_local TIME NOT NULL,
    session_close_local TIME NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_exchanges_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS instruments (
    id UUID PRIMARY KEY,
    exchange_id UUID NOT NULL REFERENCES exchanges(id),
    symbol VARCHAR(24) NOT NULL,
    name VARCHAR(160) NOT NULL,
    sector VARCHAR(80),
    currency VARCHAR(3) NOT NULL,
    instrument_type VARCHAR(32) NOT NULL CHECK (instrument_type IN ('EQUITY', 'ETF', 'INDEX')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_instruments_exchange_symbol UNIQUE (exchange_id, symbol)
);

CREATE INDEX IF NOT EXISTS idx_instruments_symbol ON instruments (symbol);
CREATE INDEX IF NOT EXISTS idx_instruments_exchange_active ON instruments (exchange_id, active);

CREATE TABLE IF NOT EXISTS market_data_bars (
    id UUID PRIMARY KEY,
    instrument_id UUID NOT NULL REFERENCES instruments(id),
    bar_interval VARCHAR(16) NOT NULL CHECK (bar_interval IN ('1d', '1h', '15m')),
    bar_timestamp TIMESTAMPTZ NOT NULL,
    open_price NUMERIC(19, 4) NOT NULL CHECK (open_price > 0),
    high_price NUMERIC(19, 4) NOT NULL CHECK (high_price > 0),
    low_price NUMERIC(19, 4) NOT NULL CHECK (low_price > 0),
    close_price NUMERIC(19, 4) NOT NULL CHECK (close_price > 0),
    volume BIGINT NOT NULL CHECK (volume >= 0),
    currency VARCHAR(3) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    adjusted BOOLEAN NOT NULL DEFAULT FALSE,
    simulated BOOLEAN NOT NULL DEFAULT TRUE,
    source_timezone VARCHAR(64) NOT NULL,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_bar_high_ge_others CHECK (
        high_price >= open_price AND high_price >= close_price AND high_price >= low_price
    ),
    CONSTRAINT chk_bar_low_le_others CHECK (
        low_price <= open_price AND low_price <= close_price AND low_price <= high_price
    ),
    CONSTRAINT uq_market_data_bars UNIQUE (
        instrument_id, bar_interval, bar_timestamp, provider, adjusted
    )
);

CREATE INDEX IF NOT EXISTS idx_market_data_bars_lookup
    ON market_data_bars (instrument_id, bar_interval, provider, adjusted, bar_timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_market_data_bars_timestamp
    ON market_data_bars (bar_timestamp DESC);

CREATE TABLE IF NOT EXISTS provider_sync_runs (
    id UUID PRIMARY KEY,
    provider VARCHAR(64) NOT NULL,
    instrument_id UUID REFERENCES instruments(id),
    symbol VARCHAR(24),
    bar_interval VARCHAR(16),
    adjusted BOOLEAN NOT NULL DEFAULT FALSE,
    simulated BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(24) NOT NULL CHECK (status IN ('SUCCESS', 'FAILED')),
    bars_fetched INTEGER NOT NULL DEFAULT 0 CHECK (bars_fetched >= 0),
    bars_inserted INTEGER NOT NULL DEFAULT 0 CHECK (bars_inserted >= 0),
    bars_skipped INTEGER NOT NULL DEFAULT 0 CHECK (bars_skipped >= 0),
    range_from TIMESTAMPTZ,
    range_to TIMESTAMPTZ,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    error_message VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS idx_provider_sync_runs_lookup
    ON provider_sync_runs (provider, instrument_id, bar_interval, completed_at DESC);
CREATE INDEX IF NOT EXISTS idx_provider_sync_runs_completed
    ON provider_sync_runs (completed_at DESC);

-- Reference catalog seed: BIST exchange + 10 major BIST equity metadata entries (no prices seeded)
INSERT INTO exchanges (
    id, code, name, country_code, currency, timezone, session_open_local, session_close_local, active
) VALUES (
    '11111111-1111-1111-1111-000000000001',
    'BIST',
    'Borsa Istanbul',
    'TR',
    'TRY',
    'Europe/Istanbul',
    '10:00:00',
    '18:00:00',
    TRUE
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO instruments (
    id, exchange_id, symbol, name, sector, currency, instrument_type, active
) VALUES
    ('22222222-2222-2222-2222-000000000001', '11111111-1111-1111-1111-000000000001', 'THYAO', 'Turk Hava Yollari A.O.', 'Transportation', 'TRY', 'EQUITY', TRUE),
    ('22222222-2222-2222-2222-000000000002', '11111111-1111-1111-1111-000000000001', 'GARAN', 'Turkiye Garanti Bankasi A.S.', 'Banking', 'TRY', 'EQUITY', TRUE),
    ('22222222-2222-2222-2222-000000000003', '11111111-1111-1111-1111-000000000001', 'AKBNK', 'Akbank T.A.S.', 'Banking', 'TRY', 'EQUITY', TRUE),
    ('22222222-2222-2222-2222-000000000004', '11111111-1111-1111-1111-000000000001', 'ASELS', 'Aselsan Elektronik Sanayi ve Ticaret A.S.', 'Defense & Electronics', 'TRY', 'EQUITY', TRUE),
    ('22222222-2222-2222-2222-000000000005', '11111111-1111-1111-1111-000000000001', 'BIMAS', 'BIM Birlesik Magazalar A.S.', 'Retail', 'TRY', 'EQUITY', TRUE),
    ('22222222-2222-2222-2222-000000000006', '11111111-1111-1111-1111-000000000001', 'EREGL', 'Eregli Demir ve Celik Fabrikalari T.A.S.', 'Basic Materials', 'TRY', 'EQUITY', TRUE),
    ('22222222-2222-2222-2222-000000000007', '11111111-1111-1111-1111-000000000001', 'KCHOL', 'Koc Holding A.S.', 'Holdings', 'TRY', 'EQUITY', TRUE),
    ('22222222-2222-2222-2222-000000000008', '11111111-1111-1111-1111-000000000001', 'SAHOL', 'Haci Omer Sabanci Holding A.S.', 'Holdings', 'TRY', 'EQUITY', TRUE),
    ('22222222-2222-2222-2222-000000000009', '11111111-1111-1111-1111-000000000001', 'SISE', 'Turkiye Sise ve Cam Fabrikalari A.S.', 'Industrials', 'TRY', 'EQUITY', TRUE),
    ('22222222-2222-2222-2222-000000000010', '11111111-1111-1111-1111-000000000001', 'TUPRS', 'Tupras-Turkiye Petrol Rafinerileri A.S.', 'Energy', 'TRY', 'EQUITY', TRUE)
ON CONFLICT (exchange_id, symbol) DO NOTHING;
