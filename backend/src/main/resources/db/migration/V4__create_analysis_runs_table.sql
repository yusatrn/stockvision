-- StockVision AI — Phase 4 Technical Analysis Runs Table
-- Persists calculation engine version, parameters configuration, data range, and sufficiency status in UTC.

CREATE TABLE IF NOT EXISTS analysis_runs (
    id UUID PRIMARY KEY,
    instrument_id UUID NOT NULL REFERENCES instruments(id),
    symbol VARCHAR(24) NOT NULL,
    bar_interval VARCHAR(16) NOT NULL CHECK (bar_interval IN ('1d', '1h', '15m')),
    provider VARCHAR(64) NOT NULL,
    adjusted BOOLEAN NOT NULL DEFAULT FALSE,
    simulated BOOLEAN NOT NULL DEFAULT TRUE,
    run_type VARCHAR(32) NOT NULL CHECK (run_type IN ('TECHNICAL_INDICATORS', 'TECHNICAL_SCORE')),
    engine_version VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('READY', 'PARTIAL', 'INSUFFICIENT_DATA')),
    parameters_json TEXT NOT NULL,
    bar_count INTEGER NOT NULL CHECK (bar_count >= 0),
    range_from TIMESTAMPTZ,
    range_to TIMESTAMPTZ,
    latest_bar_timestamp TIMESTAMPTZ,
    calculated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_analysis_runs_instrument_calc
    ON analysis_runs (instrument_id, bar_interval, run_type, calculated_at DESC);

CREATE INDEX IF NOT EXISTS idx_analysis_runs_calculated_at
    ON analysis_runs (calculated_at DESC);
