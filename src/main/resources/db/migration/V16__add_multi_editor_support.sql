-- V16: Добавление мульти-редактора
ALTER TABLE document_edit_sessions ADD COLUMN IF NOT EXISTS editor_type VARCHAR(20) DEFAULT 'ONLYOFFICE';
UPDATE document_edit_sessions SET editor_type = 'ONLYOFFICE' WHERE editor_type IS NULL;
ALTER TABLE document_edit_sessions ALTER COLUMN editor_type SET NOT NULL;
ALTER TABLE document_edit_sessions ALTER COLUMN onlyoffice_key DROP NOT NULL;
ALTER TABLE document_edit_sessions DROP CONSTRAINT IF EXISTS document_edit_sessions_onlyoffice_key_key;
ALTER TABLE document_edit_sessions ADD COLUMN IF NOT EXISTS wopi_file_id VARCHAR(128);
ALTER TABLE document_edit_sessions ADD COLUMN IF NOT EXISTS wopi_lock_value VARCHAR(255);
ALTER TABLE document_edit_sessions ADD COLUMN IF NOT EXISTS wopi_lock_expires_at TIMESTAMP;
ALTER TABLE companies ADD COLUMN IF NOT EXISTS preferred_editor VARCHAR(20) DEFAULT 'ONLYOFFICE';
UPDATE companies SET preferred_editor = 'ONLYOFFICE' WHERE preferred_editor IS NULL;