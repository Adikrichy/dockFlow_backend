-- Добавление refresh token функциональности

ALTER TABLE users ADD COLUMN IF NOT EXISTS refresh_token VARCHAR(500);
ALTER TABLE users ADD COLUMN IF NOT EXISTS refresh_token_expiry TIMESTAMP;

-- Индекс для поиска пользователей по refresh token (для валидации)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_refresh_token ON users(refresh_token) WHERE refresh_token IS NOT NULL;

-- Индекс для поиска истекших refresh tokens (для cleanup)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_refresh_token_expiry ON users(refresh_token_expiry) WHERE refresh_token_expiry IS NOT NULL;
