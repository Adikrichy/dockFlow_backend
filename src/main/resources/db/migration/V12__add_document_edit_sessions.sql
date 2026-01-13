CREATE TABLE IF NOT EXISTS document_edit_sessions (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL,
    session_key VARCHAR(64) NOT NULL UNIQUE,
    onlyoffice_key VARCHAR(128) NOT NULL UNIQUE,
    working_docx_path VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    committed_at TIMESTAMP,
    CONSTRAINT fk_document_edit_sessions_document FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    CONSTRAINT fk_document_edit_sessions_created_by FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_document_edit_sessions_document ON document_edit_sessions(document_id);
CREATE INDEX IF NOT EXISTS idx_document_edit_sessions_status ON document_edit_sessions(status);
