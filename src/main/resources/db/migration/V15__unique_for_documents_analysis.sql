ALTER TABLE ai.document_ai_analysis
    ADD CONSTRAINT uq_doc_version UNIQUE (document_id, version_id);
