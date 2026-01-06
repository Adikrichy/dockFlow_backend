package org.aldousdev.dockflowbackend.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.workflow.entity.Task;
import org.aldousdev.dockflowbackend.workflow.entity.WorkflowAuditLog;
import org.aldousdev.dockflowbackend.workflow.entity.WorkflowInstance;
import org.aldousdev.dockflowbackend.workflow.repository.WorkflowAuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowAuditService {
    private final WorkflowAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public enum ActionType {
        WORKFLOW_STARTED,
        WORKFLOW_COMPLETED,
        WORKFLOW_REJECTED,
        TASK_CREATED,
        TASK_APPROVED,
        TASK_REJECTED,
        TASK_CANCELLED,
        TASK_REASSIGNED,
        ROUTING_RULE_APPLIED
    }

    @Transactional
    public void logWorkflowStarted(WorkflowInstance instance, User initiatedBy) {
        log.info("Audit: Workflow {} started by {}", instance.getId(), initiatedBy.getEmail());
        
        WorkflowAuditLog log = WorkflowAuditLog.builder()
                .workflowInstance(instance)
                .performedBy(initiatedBy)
                .actionType(ActionType.WORKFLOW_STARTED.name())
                .description("Workflow initiated for document: " + instance.getDocument().getId())
                .metadata(toJson(Map.of("documentId", instance.getDocument().getId())))
                .ipAddress(getClientIp())
                .createdAt(LocalDateTime.now())
                .build();
        
        auditLogRepository.save(log);
    }

    @Transactional
    public void logTaskCreated(Task task, String roleName) {
        log.info("Audit: Task {} created for role {}", task.getId(), roleName);
        
        WorkflowAuditLog log = WorkflowAuditLog.builder()
                .workflowInstance(task.getWorkflowInstance())
                .task(task)
                .actionType(ActionType.TASK_CREATED.name())
                .description("Task created for step: " + task.getStepOrder())
                .metadata(toJson(Map.of(
                    "stepOrder", task.getStepOrder(),
                    "roleLevel", task.getRequiredRoleLevel(),
                    "roleName", roleName
                )))
                .createdAt(LocalDateTime.now())
                .build();
        
        auditLogRepository.save(log);
    }

    @Transactional
    public void logTaskApproved(Task task, User approvedBy, String comment) {
        log.info("Audit: Task {} approved by {}", task.getId(), approvedBy.getEmail());
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("stepOrder", task.getStepOrder());
        metadata.put("roleLevel", task.getRequiredRoleLevel());
        metadata.put("approvalTime", LocalDateTime.now());
        if (comment != null) {
            metadata.put("comment", comment);
        }
        
        WorkflowAuditLog log = WorkflowAuditLog.builder()
                .workflowInstance(task.getWorkflowInstance())
                .task(task)
                .performedBy(approvedBy)
                .actionType(ActionType.TASK_APPROVED.name())
                .description("Task approved at step: " + task.getStepOrder())
                .metadata(toJson(metadata))
                .ipAddress(getClientIp())
                .createdAt(LocalDateTime.now())
                .build();
        
        auditLogRepository.save(log);
    }

    @Transactional
    public void logTaskRejected(Task task, User rejectedBy, String comment) {
        log.info("Audit: Task {} rejected by {}", task.getId(), rejectedBy.getEmail());
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("stepOrder", task.getStepOrder());
        metadata.put("roleLevel", task.getRequiredRoleLevel());
        metadata.put("rejectionTime", LocalDateTime.now());
        if (comment != null) {
            metadata.put("comment", comment);
        }
        
        WorkflowAuditLog log = WorkflowAuditLog.builder()
                .workflowInstance(task.getWorkflowInstance())
                .task(task)
                .performedBy(rejectedBy)
                .actionType(ActionType.TASK_REJECTED.name())
                .description("Task rejected at step: " + task.getStepOrder())
                .metadata(toJson(metadata))
                .ipAddress(getClientIp())
                .createdAt(LocalDateTime.now())
                .build();
        
        auditLogRepository.save(log);
    }

    @Transactional
    public void logTaskCancelled(Task task, String reason) {
        log.info("Audit: Task {} cancelled", task.getId());
        
        WorkflowAuditLog log = WorkflowAuditLog.builder()
                .workflowInstance(task.getWorkflowInstance())
                .task(task)
                .actionType(ActionType.TASK_CANCELLED.name())
                .description("Task cancelled - " + reason)
                .metadata(toJson(Map.of("reason", reason)))
                .createdAt(LocalDateTime.now())
                .build();
        
        auditLogRepository.save(log);
    }

    @Transactional
    public void logWorkflowCompleted(WorkflowInstance instance) {
        log.info("Audit: Workflow {} completed", instance.getId());
        
        WorkflowAuditLog log = WorkflowAuditLog.builder()
                .workflowInstance(instance)
                .actionType(ActionType.WORKFLOW_COMPLETED.name())
                .description("Workflow completed successfully")
                .metadata(toJson(Map.of("completionTime", LocalDateTime.now())))
                .createdAt(LocalDateTime.now())
                .build();
        
        auditLogRepository.save(log);
    }

    @Transactional
    public void logWorkflowRejected(WorkflowInstance instance, String reason) {
        log.info("Audit: Workflow {} rejected", instance.getId());
        
        WorkflowAuditLog log = WorkflowAuditLog.builder()
                .workflowInstance(instance)
                .actionType(ActionType.WORKFLOW_REJECTED.name())
                .description("Workflow rejected - " + reason)
                .metadata(toJson(Map.of("reason", reason)))
                .createdAt(LocalDateTime.now())
                .build();
        
        auditLogRepository.save(log);
    }

    @Transactional
    public void logRoutingRuleApplied(WorkflowInstance instance, Integer fromStep, Integer toStep) {
        log.info("Audit: Routing rule applied - step {} -> {}", fromStep, toStep);
        
        WorkflowAuditLog log = WorkflowAuditLog.builder()
                .workflowInstance(instance)
                .actionType(ActionType.ROUTING_RULE_APPLIED.name())
                .description("Workflow returned from step " + fromStep + " to step " + toStep)
                .metadata(toJson(Map.of(
                    "fromStep", fromStep,
                    "toStep", toStep
                )))
                .createdAt(LocalDateTime.now())
                .build();
        
        auditLogRepository.save(log);
    }

    /**
     * Get full history for workflow
     */
    public java.util.List<WorkflowAuditLog> getWorkflowHistory(WorkflowInstance instance) {
        return auditLogRepository.findByWorkflowInstanceOrderedByTime(instance);
    }

    /**
     * Get history for task
     */
    public java.util.List<WorkflowAuditLog> getTaskHistory(Task task) {
        return auditLogRepository.findByTask(task);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Failed to serialize metadata to JSON", e);
            return obj.toString();
        }
    }

    private String getClientIp() {
        try {
            ServletRequestAttributes requestAttributes = 
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            
            if (requestAttributes == null) {
                return "UNKNOWN";
            }
            
            HttpServletRequest request = requestAttributes.getRequest();
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0];
            }
            
            return request.getRemoteAddr();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
}
