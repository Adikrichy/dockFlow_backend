-- Добавление поля condition для conditional routing

ALTER TABLE routing_rules ADD COLUMN IF NOT EXISTS condition VARCHAR(500);

-- Индекс для поиска routing rules с условиями
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_routing_rules_condition
    ON routing_rules(condition) WHERE condition IS NOT NULL;
