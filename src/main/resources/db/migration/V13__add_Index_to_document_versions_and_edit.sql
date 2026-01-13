-- Migration: Remove unique constraint from sha256_hash in document_versions table
-- This allows the same file to be uploaded multiple times or used in different document versions
ALTER TABLE document_versions DROP CONSTRAINT IF EXISTS ukjt731dkdbpquwe6wl69f2teqk;
ALTER TABLE document_versions DROP CONSTRAINT IF EXISTS uk_document_versions_sha256_hash;
-- Optional: Add index for performance (without unique constraint)
CREATE INDEX IF NOT EXISTS idx_document_versions_sha256_hash ON document_versions(sha256_hash);