package org.aldousdev.dockflowbackend.workflow.repository;

import org.aldousdev.dockflowbackend.workflow.entity.RoutingRule;
import org.aldousdev.dockflowbackend.workflow.entity.WorkflowTemplate;
import org.aldousdev.dockflowbackend.workflow.enums.RoutingType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoutingRuleRepository extends JpaRepository<RoutingRule, Long> {
    List<RoutingRule> findByTemplate(WorkflowTemplate template);

    Optional<RoutingRule> findByTemplateAndStepOrderAndRoutingType(
            WorkflowTemplate template,
            Integer stepOrder,
            RoutingType routingType
    );

    List<RoutingRule> findByTemplateAndStepOrder(WorkflowTemplate template, Integer stepOrder);

    void deleteByTemplate(WorkflowTemplate template);
}
