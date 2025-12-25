package org.aldousdev.dockflowbackend.workflow.repository;

import org.aldousdev.dockflowbackend.workflow.entity.WorkflowTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowTemplateRepository extends JpaRepository<WorkflowTemplate, Long> {
    List<WorkflowTemplate> findByCompanyIdAndIsActive(Long companyId, Boolean isActive);
}
