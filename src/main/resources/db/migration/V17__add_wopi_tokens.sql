-- V17: WOPI токены
CREATE TABLE IF NOT EXISTS wopi_tokens (
                                           id BIGSERIAL PRIMARY KEY,
                                           token VARCHAR(128) NOT NULL UNIQUE,
    session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    scopes VARCHAR(255),
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
    );