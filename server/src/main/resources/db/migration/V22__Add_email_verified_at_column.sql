-- Email verification via Android Digital Credentials API — data wiring only.
--
-- NOTE: this worktree was branched from origin/main. The user's local checkout
-- already has V20__Add_resource_routes.sql and V21__Add_revoked_refresh_tokens.sql
-- as untracked migrations. **Before merging this branch back, rename this file
-- to V22__Add_email_verified_at_column.sql** so the Flyway sequence stays
-- V19 → V20 (resource routes) → V21 (revoked refresh tokens) → V22 (this).
--
-- Adds:
--   1. accounts.email_verified_at — timestamp recording when the email on the
--      account was last verified (via DC API today; future flows may also set
--      this). The existing accounts.email_verified BOOLEAN remains the canonical
--      truthy flag; this column is purely an audit/recency signal.
--
--   2. pending_email_verifications — short-lived (~5 min) one-row-per-attempt
--      table that binds a server-issued nonce + transaction id to an account
--      while the Android client drives the Credential Manager UI. Burned on
--      complete() regardless of outcome.

ALTER TABLE accounts
    ADD COLUMN email_verified_at TIMESTAMPTZ NULL;

CREATE TABLE pending_email_verifications (
    transaction_id UUID         PRIMARY KEY,
    account_id     UUID         NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    nonce          VARCHAR(128) NOT NULL,
    expires_at     TIMESTAMPTZ  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pev_account ON pending_email_verifications (account_id);
CREATE INDEX idx_pev_expires ON pending_email_verifications (expires_at);
