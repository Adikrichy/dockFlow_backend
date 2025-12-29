-- Добавление таблиц для security audit logging

CREATE TABLE IF NOT EXISTS security_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    user_id BIGINT,
    user_email VARCHAR(255),
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    details VARCHAR(1000),
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Индексы для производительности
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_security_audit_user ON security_audit_logs(user_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_security_audit_event ON security_audit_logs(event_type);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_security_audit_timestamp ON security_audit_logs(timestamp);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_security_audit_ip ON security_audit_logs(ip_address);

-- Индекс для поиска подозрительной активности
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_security_audit_suspicious
    ON security_audit_logs(event_type, ip_address, timestamp)
    WHERE event_type IN ('LOGIN_FAILED', 'RATE_LIMIT_EXCEEDED', 'SUSPICIOUS_ACTIVITY');
