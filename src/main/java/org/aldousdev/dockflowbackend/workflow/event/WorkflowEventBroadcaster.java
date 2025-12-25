package org.aldousdev.dockflowbackend.workflow.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkflowEventBroadcaster {
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Отправляет уведомление о запуске workflow
     */
    public void broadcastWorkflowStarted(Long companyId, Long workflowInstanceId, Long documentId) {
        log.info("Broadcasting workflow started event");
        
        Map<String, Object> event = new HashMap<>();
        event.put("type", "WORKFLOW_STARTED");
        event.put("workflowInstanceId", workflowInstanceId);
        event.put("documentId", documentId);
        event.put("timestamp", LocalDateTime.now());
        
        messagingTemplate.convertAndSend("/topic/workflow/company/" + companyId, (Object) event);
    }

    /**
     * Отправляет уведомление о новой task
     */
    public void broadcastTaskCreated(Long companyId, Long taskId, String roleName) {
        log.info("Broadcasting task created event for role: {}", roleName);
        
        Map<String, Object> event = new HashMap<>();
        event.put("type", "TASK_CREATED");
        event.put("taskId", taskId);
        event.put("roleName", roleName);
        event.put("timestamp", LocalDateTime.now());
        
        messagingTemplate.convertAndSend("/topic/workflow/company/" + companyId, (Object) event);
        messagingTemplate.convertAndSend("/topic/workflow/role/" + roleName, (Object) event);
    }

    /**
     * Отправляет уведомление об одобрении task
     */
    public void broadcastTaskApproved(Long companyId, Long taskId, String approvedBy) {
        log.info("Broadcasting task approved event");
        
        Map<String, Object> event = new HashMap<>();
        event.put("type", "TASK_APPROVED");
        event.put("taskId", taskId);
        event.put("approvedBy", approvedBy);
        event.put("timestamp", LocalDateTime.now());
        
        messagingTemplate.convertAndSend("/topic/workflow/company/" + companyId, (Object) event);
    }

    /**
     * Отправляет уведомление об отклонении task
     */
    public void broadcastTaskRejected(Long companyId, Long taskId, String rejectedBy) {
        log.info("Broadcasting task rejected event");
        
        Map<String, Object> event = new HashMap<>();
        event.put("type", "TASK_REJECTED");
        event.put("taskId", taskId);
        event.put("rejectedBy", rejectedBy);
        event.put("timestamp", LocalDateTime.now());
        
        messagingTemplate.convertAndSend("/topic/workflow/company/" + companyId, (Object) event);
    }

    /**
     * Отправляет уведомление о завершении workflow
     */
    public void broadcastWorkflowCompleted(Long companyId, Long workflowInstanceId) {
        log.info("Broadcasting workflow completed event");
        
        Map<String, Object> event = new HashMap<>();
        event.put("type", "WORKFLOW_COMPLETED");
        event.put("workflowInstanceId", workflowInstanceId);
        event.put("timestamp", LocalDateTime.now());
        
        messagingTemplate.convertAndSend("/topic/workflow/company/" + companyId, (Object) event);
    }

    /**
     * Отправляет уведомление об отклонении workflow
     */
    public void broadcastWorkflowRejected(Long companyId, Long workflowInstanceId, String reason) {
        log.info("Broadcasting workflow rejected event");
        
        Map<String, Object> event = new HashMap<>();
        event.put("type", "WORKFLOW_REJECTED");
        event.put("workflowInstanceId", workflowInstanceId);
        event.put("reason", reason);
        event.put("timestamp", LocalDateTime.now());
        
        messagingTemplate.convertAndSend("/topic/workflow/company/" + companyId, (Object) event);
    }
}
