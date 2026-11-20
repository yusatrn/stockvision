-- StockVision AI — Phase 2 Users Table Migration
-- Stores normalized lowercase emails, BCrypt password hashes, roles, status, and UTC timestamps.

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    display_name VARCHAR(100),
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    status VARCHAR(32) NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED', 'LOCKED')),
    timezone VARCHAR(64) NOT NULL DEFAULT 'Europe/Istanbul',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE INDEX IF NOT EXISTS idx_users_status ON users (status);
