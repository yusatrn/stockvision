-- StockVision AI — Phase 8 AI Explanations Table
-- Persists schema-validated AI or deterministic template explanations bound to analysis & score runs in UTC.

CREATE TABLE IF NOT EXISTS ai_explanations (
    id UUID PRIMARY KEY,
    analysis_run_id UUID NOT NULL REFERENCES analysis_runs(id),
    score_run_id UUID NOT NULL REFERENCES technical_score_runs(id),
    instrument_id UUID NOT NULL REFERENCES instruments(id),
    symbol VARCHAR(24) NOT NULL,
    bar_interval VARCHAR(16) NOT NULL CHECK (bar_interval IN ('1d', '1h', '15m')),
    provider_id VARCHAR(64) NOT NULL,
    requested_provider_id VARCHAR(64) NOT NULL,
    model_name VARCHAR(120) NOT NULL,
    prompt_version VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('GENERATED', 'FALLBACK', 'INSUFFICIENT_DATA')),
    fallback_used BOOLEAN NOT NULL DEFAULT FALSE,
    fallback_reason VARCHAR(64),
    simulated_data BOOLEAN NOT NULL DEFAULT TRUE,
    stale_data BOOLEAN NOT NULL DEFAULT FALSE,
    structured_output_json TEXT NOT NULL,
    disclaimer TEXT NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_explanations_score_run
    ON ai_explanations (score_run_id, prompt_version, requested_provider_id, generated_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_explanations_analysis_run
    ON ai_explanations (analysis_run_id, generated_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_explanations_instrument
    ON ai_explanations (instrument_id, bar_interval, generated_at DESC);
