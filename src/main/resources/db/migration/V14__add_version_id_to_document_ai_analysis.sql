-- Add version_id column to document_ai_analysis table for document version tracking
ALTER TABLE ai.document_ai_analysis 
ADD COLUMN version_id BIGINT;

-- Add index for faster lookups by version
CREATE INDEX idx_doc_ai_version_id ON ai.document_ai_analysis(version_id);

-- Update existing records to have NULL version_id (will be populated for new analyses)
-- This maintains backward compatibility