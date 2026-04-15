-- Auth Service Schema
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    role VARCHAR(50) NOT NULL DEFAULT 'USER',
    is_active BOOLEAN NOT NULL DEFAULT true,
    is_email_verified BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_role CHECK (role IN ('USER','ADMIN','MODERATOR'))
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(512) NOT NULL UNIQUE,
    issued_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL,
    is_revoked BOOLEAN NOT NULL DEFAULT false,
    device_info VARCHAR(255)
);

CREATE TABLE token_blacklist (
    jti VARCHAR(255) PRIMARY KEY,    -- JWT ID
    user_id UUID NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    blacklisted_at TIMESTAMP NOT NULL DEFAULT NOW()
);


CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_hash ON refresh_tokens(token_hash);
CREATE INDEX idx_token_blacklist_expires ON token_blacklist(expires_at);

-- Auto-cleanup expired blacklisted tokens (triggered manually or via scheduler)
CREATE OR REPLACE FUNCTION cleanup_expired_tokens() RETURNS void AS $$
BEGIN
    DELETE FROM token_blacklist WHERE expires_at < NOW();
    DELETE FROM refresh_tokens WHERE expires_at < NOW() AND is_revoked = true;
END;
$$ LANGUAGE plpgsql;
