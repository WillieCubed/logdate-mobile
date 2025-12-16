-- Initial schema for LogDate account management and passkey authentication

-- Extension for UUID support
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Accounts table
CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username VARCHAR(50) UNIQUE NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    email VARCHAR(255),
    bio TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_sign_in_at TIMESTAMP WITH TIME ZONE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    preferences JSONB DEFAULT '{}',
    
    -- Constraints
    CONSTRAINT username_length CHECK (LENGTH(username) >= 3),
    CONSTRAINT username_format CHECK (username ~ '^[a-zA-Z0-9_]+$')
);

-- Passkeys table for WebAuthn credentials
CREATE TABLE passkeys (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    credential_id TEXT UNIQUE NOT NULL,
    public_key TEXT NOT NULL,
    sign_count BIGINT NOT NULL DEFAULT 0,
    nickname VARCHAR(100),
    device_type VARCHAR(50) NOT NULL DEFAULT 'platform', -- 'platform' or 'cross-platform'
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_used_at TIMESTAMP WITH TIME ZONE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    
    -- Additional WebAuthn data stored as JSON
    webauthn_data JSONB DEFAULT '{}'
);

-- Sessions table for temporary authentication/registration sessions
CREATE TABLE sessions (
    id VARCHAR(64) PRIMARY KEY,
    temporary_user_id UUID NOT NULL,
    challenge TEXT NOT NULL,
    session_type VARCHAR(20) NOT NULL, -- 'ACCOUNT_CREATION' or 'AUTHENTICATION'
    username VARCHAR(50),
    device_info JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    
    -- Constraints
    CONSTRAINT session_type_valid CHECK (session_type IN ('ACCOUNT_CREATION', 'AUTHENTICATION'))
);

-- Challenges table for WebAuthn challenge management
CREATE TABLE webauthn_challenges (
    challenge TEXT PRIMARY KEY,
    user_id UUID NOT NULL,
    challenge_type VARCHAR(20) NOT NULL, -- 'registration' or 'authentication'
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    
    -- Constraints
    CONSTRAINT challenge_type_valid CHECK (challenge_type IN ('registration', 'authentication'))
);

-- Indexes for performance
CREATE INDEX idx_accounts_username ON accounts(username);
CREATE INDEX idx_accounts_email ON accounts(email);
CREATE INDEX idx_accounts_created_at ON accounts(created_at);
CREATE INDEX idx_accounts_active ON accounts(is_active);

CREATE INDEX idx_passkeys_account_id ON passkeys(account_id);
CREATE INDEX idx_passkeys_credential_id ON passkeys(credential_id);
CREATE INDEX idx_passkeys_active ON passkeys(is_active);
CREATE INDEX idx_passkeys_last_used ON passkeys(last_used_at);

CREATE INDEX idx_sessions_expires_at ON sessions(expires_at);
CREATE INDEX idx_sessions_used ON sessions(is_used);
CREATE INDEX idx_sessions_type ON sessions(session_type);

CREATE INDEX idx_webauthn_challenges_expires_at ON webauthn_challenges(expires_at);
CREATE INDEX idx_webauthn_challenges_used ON webauthn_challenges(is_used);
CREATE INDEX idx_webauthn_challenges_user_id ON webauthn_challenges(user_id);

-- Comments for documentation
COMMENT ON TABLE accounts IS 'User accounts with basic profile information';
COMMENT ON TABLE passkeys IS 'WebAuthn passkey credentials for user authentication';
COMMENT ON TABLE sessions IS 'Temporary sessions for account creation and authentication flows';
COMMENT ON TABLE webauthn_challenges IS 'WebAuthn challenges for registration and authentication';

COMMENT ON COLUMN accounts.preferences IS 'User preferences stored as JSON';
COMMENT ON COLUMN passkeys.webauthn_data IS 'Additional WebAuthn credential data';
COMMENT ON COLUMN sessions.device_info IS 'Device information for the session';