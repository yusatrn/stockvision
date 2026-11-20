-- StockVision AI — Phase 5 Explainable Technical Score Runs Table
-- Persists algorithm version, configurable weights, component breakdowns, and UI-ready explanations in UTC.

CREATE TABLE IF NOT EXISTS technical_score_runs (
    id UUID PRIMARY KEY,
    analysis_run_id UUID NOT NULL REFERENCES analysis_runs(id),
    instrument_id UUID NOT NULL REFERENCES instruments(id),
    symbol VARCHAR(24) NOT NULL,
    exchange_code VARCHAR(16) NOT NULL,
    bar_interval VARCHAR(16) NOT NULL CHECK (bar_interval IN ('1d', '1h', '15m')),
    provider VARCHAR(64) NOT NULL,
    adjusted BOOLEAN NOT NULL DEFAULT FALSE,
    simulated BOOLEAN NOT NULL DEFAULT TRUE,
    algorithm_version VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('READY', 'INSUFFICIENT_DATA')),
    stale_data BOOLEAN NOT NULL DEFAULT FALSE,
    final_score NUMERIC(6, 2) CHECK (final_score IS NULL OR (final_score >= 0 AND final_score <= 100)),
    score_regime VARCHAR(40) NOT NULL,
    trend_direction VARCHAR(24) NOT NULL CHECK (trend_direction IN ('BULLISH', 'NEUTRAL', 'BEARISH', 'UNKNOWN')),
    latest_close NUMERIC(19, 4),
    latest_rsi NUMERIC(10, 6),
    weights_json TEXT NOT NULL,
    components_json TEXT NOT NULL,
    explanation_json TEXT NOT NULL,
    range_from TIMESTAMPTZ,
    range_to TIMESTAMPTZ,
    latest_bar_timestamp TIMESTAMPTZ,
    calculated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_technical_score_runs_instrument
    ON technical_score_runs (instrument_id, bar_interval, provider, adjusted, calculated_at DESC);

CREATE INDEX IF NOT EXISTS idx_technical_score_runs_calculated
    ON technical_score_runs (calculated_at DESC);
