-- Добавление полей для conditional routing в documents

ALTER TABLE documents ADD COLUMN IF NOT EXISTS amount DECIMAL(19,2);
ALTER TABLE documents ADD COLUMN IF NOT EXISTS priority VARCHAR(20) DEFAULT 'NORMAL';
ALTER TABLE documents ADD COLUMN IF NOT EXISTS document_type VARCHAR(20) DEFAULT 'GENERAL';
ALTER TABLE documents ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'DRAFT';
ALTER TABLE documents ADD COLUMN IF NOT EXISTS metadata TEXT;

-- Индексы для conditional routing
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_documents_amount ON documents(amount);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_documents_priority ON documents(priority);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_documents_type ON documents(document_type);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_documents_status ON documents(status);

-- Индекс для поиска документов по условиям (композитный)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_documents_conditional
    ON documents(amount, priority, document_type, status, company_id);
