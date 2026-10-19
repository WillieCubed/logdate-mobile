-- Single-use OAuth values: DPoP proof IDs and PKCE code challenges. Every server instance shares
-- this table, so a value used on one instance is refused on all of them. value_hash is the SHA-256
-- of the namespaced value, so the values themselves are never stored. Expired rows are deleted a
-- few at a time each time a new value is recorded.
CREATE TABLE oauth_used_values (
    value_hash VARCHAR(64) PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_oauth_used_values_expires_at ON oauth_used_values(expires_at);
