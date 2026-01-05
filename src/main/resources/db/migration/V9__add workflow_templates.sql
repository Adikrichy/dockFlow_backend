ALTER TABLE workflow_templates
    ADD COLUMN allowed_role_levels INTEGER[] DEFAULT ARRAY[100]