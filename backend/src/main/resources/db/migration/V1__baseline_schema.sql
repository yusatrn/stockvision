-- StockVision AI — Phase 1 Baseline Migration
-- No business module tables are created in Phase 1.
-- This baseline verifies Flyway execution and records schema foundation metadata in UTC.

CREATE TABLE IF NOT EXISTS system_schema_metadata (
    meta_key VARCHAR(100) PRIMARY KEY,
    meta_value VARCHAR(255) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO system_schema_metadata (meta_key, meta_value)
VALUES ('foundation_version', '1')
ON CONFLICT (meta_key) DO NOTHING;
