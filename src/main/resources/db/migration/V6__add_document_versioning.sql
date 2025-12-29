-- Добавление системы версионирования документов

CREATE TABLE IF NOT EXISTS document_versions (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    version_number INT NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) DEFAULT 'application/pdf',
    file_size BIGINT,
    sha256_hash VARCHAR(64) UNIQUE,
    change_description VARCHAR(500),
    change_type VARCHAR(100),
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    workflow_metadata TEXT,
    is_signed BOOLEAN DEFAULT FALSE,
    has_watermark BOOLEAN DEFAULT FALSE,
    is_current BOOLEAN DEFAULT FALSE,

    FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    FOREIGN KEY (created_by) REFERENCES users(id)
);

-- Индексы для производительности
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_document_versions_document ON document_versions(document_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_document_versions_number ON document_versions(document_id, version_number);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_document_versions_created ON document_versions(document_id, created_at);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_document_versions_current ON document_versions(document_id, is_current) WHERE is_current = true;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_document_versions_hash ON document_versions(sha256_hash);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_document_versions_signed ON document_versions(document_id, is_signed) WHERE is_signed = true;

-- Обновляем документы: добавляем связь с текущей версией
ALTER TABLE documents ADD COLUMN IF NOT EXISTS current_version_id BIGINT;
ALTER TABLE documents ADD CONSTRAINT fk_documents_current_version
    FOREIGN KEY (current_version_id) REFERENCES document_versions(id);

-- Индекс для быстрого поиска текущей версии
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_documents_current_version ON documents(current_version_id);
