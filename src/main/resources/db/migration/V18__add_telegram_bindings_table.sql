CREATE TABLE user_telegram_bindings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    telegram_id BIGINT UNIQUE,
    linking_token VARCHAR(255) UNIQUE,
    token_expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_user_binding UNIQUE(user_id)
);

CREATE INDEX idx_telegram_binding_token ON user_telegram_bindings(linking_token);
CREATE INDEX idx_telegram_id ON user_telegram_bindings(telegram_id);
