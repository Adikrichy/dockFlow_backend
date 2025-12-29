-- Добавление индексов для улучшения производительности workflow операций

-- Индекс для быстрого поиска tasks по workflow_instance_id
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_tasks_workflow_instance ON tasks(workflow_instance_id);

-- Индекс для поиска pending tasks по роли и компании
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_tasks_pending_role_company
    ON tasks(status, required_role_name, company_id)
    WHERE status = 'PENDING';

-- Индекс для поиска tasks по workflow instance и step order
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_tasks_instance_step
    ON tasks(workflow_instance_id, step_order);

-- Индекс для поиска workflow instances по компании и статусу
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_workflow_instances_company_status
    ON workflow_instances(company_id, status);

-- Индекс для поиска документов по компании
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_documents_company
    ON documents(company_id);

-- Индекс для поиска workflow templates по компании
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_workflow_templates_company
    ON workflow_templates(company_id, is_active);

-- Индекс для поиска routing rules по template
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_routing_rules_template
    ON routing_rules(template_id);

-- Индекс для поиска audit logs по workflow instance
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_logs_workflow_instance
    ON workflow_audit_logs(workflow_instance_id);
