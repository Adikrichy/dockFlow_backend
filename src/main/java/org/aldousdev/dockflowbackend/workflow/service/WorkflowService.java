package org.aldousdev.dockflowbackend.workflow.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.workflow.dto.request.CreateWorkflowTemplateRequest;
import org.aldousdev.dockflowbackend.workflow.dto.response.TaskResponse;
import org.aldousdev.dockflowbackend.workflow.dto.response.WorkflowAuditLogResponse;
import org.aldousdev.dockflowbackend.workflow.dto.response.WorkflowInstanceResponse;
import org.aldousdev.dockflowbackend.workflow.dto.response.WorkflowTemplateResponse;
import org.aldousdev.dockflowbackend.workflow.engine.WorkflowEngine;
import org.aldousdev.dockflowbackend.workflow.entity.Document;
import org.aldousdev.dockflowbackend.workflow.entity.RoutingRule;
import org.aldousdev.dockflowbackend.workflow.entity.Task;
import org.aldousdev.dockflowbackend.workflow.entity.WorkflowInstance;
import org.aldousdev.dockflowbackend.workflow.entity.WorkflowTemplate;
import org.aldousdev.dockflowbackend.workflow.enums.RoutingType;
import org.aldousdev.dockflowbackend.workflow.enums.TaskStatus;
import org.aldousdev.dockflowbackend.workflow.event.WorkflowEventBroadcaster;
import org.aldousdev.dockflowbackend.workflow.parser.WorkflowXmlParser;
import org.aldousdev.dockflowbackend.workflow.repository.DocumentRepository;
import org.aldousdev.dockflowbackend.workflow.repository.RoutingRuleRepository;
import org.aldousdev.dockflowbackend.workflow.repository.TaskRepository;
import org.aldousdev.dockflowbackend.workflow.repository.WorkflowInstanceRepository;
import org.aldousdev.dockflowbackend.workflow.repository.WorkflowTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowService {
    private final WorkflowTemplateRepository templateRepository;
    private final WorkflowInstanceRepository instanceRepository;
    private final TaskRepository taskRepository;
    private final DocumentRepository documentRepository;
    private final RoutingRuleRepository routingRuleRepository;
    private final WorkflowEngine workflowEngine;
    private final WorkflowEventBroadcaster eventBroadcaster;
    private final WorkflowAuditService auditService;

    /**
     * Инициализирует workflow для тестирования (делегирует WorkflowEngine)
     */
    @Transactional
    public void initializeWorkflow(WorkflowInstance instance, String workflowXml) {
        workflowEngine.initializeWorkflow(instance, workflowXml);
    }

    /**
     * Одобряет задачу для тестирования (делегирует WorkflowEngine)
     */
    @Transactional
    public void approveTask(Task task, User user, String comment) {
        workflowEngine.approveTask(task, user, comment);
    }

    /**
     * Отклоняет задачу для тестирования (делегирует WorkflowEngine)
     */
    @Transactional
    public void rejectTask(Task task, User user, String comment) {
        workflowEngine.rejectTask(task, user, comment);
    }

    /**
     * Создает новый workflow template с правилами маршрутизации
     */
    @Transactional
    public WorkflowTemplateResponse createTemplate(CreateWorkflowTemplateRequest request, User createdBy) {
        log.info("Creating workflow template: {} for company: {}", 
            request.getName(), request.getCompanyId());

        WorkflowTemplate template = WorkflowTemplate.builder()
                .name(request.getName())
                .description(request.getDescription())
                .workflowXml(request.getStepsXml())
                .companyId(request.getCompanyId())
                .createdBy(createdBy)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();

        template = templateRepository.save(template);

        // Парсим и сохраняем правила маршрутизации
        try {
            WorkflowXmlParser.WorkflowDefinition definition = 
                WorkflowXmlParser.parseWorkflowDefinition(request.getStepsXml());
            
            for (WorkflowXmlParser.RoutingRule rule : definition.getRoutingRules()) {
                RoutingType routingType = RoutingType.fromXmlValue(rule.getRoutingType());
                
                RoutingRule routingRule = RoutingRule.builder()
                        .template(template)
                        .stepOrder(rule.getStepOrder())
                        .routingType(routingType)
                        .targetStep(rule.getTargetStep())
                        .condition(rule.getCondition())
                        .description(rule.getDescription())
                        .isOverrideAllowed(true)
                        .build();
                
                routingRuleRepository.save(routingRule);
            }
            
            log.info("Template created with {} routing rules", definition.getRoutingRules().size());
        } catch (Exception e) {
            log.warn("Could not parse routing rules: {}", e.getMessage());
        }

        return mapToTemplateResponse(template);
    }

    /**
     * Получает все templates компании
     */
    public List<WorkflowTemplateResponse> getCompanyTemplates(Long companyId) {
        log.debug("Fetching templates for company: {}", companyId);
        
        return templateRepository.findByCompanyIdAndIsActive(companyId, true)
                .stream()
                .map(this::mapToTemplateResponse)
                .collect(Collectors.toList());
    }

    /**
     * Получает template по ID
     */
    public WorkflowTemplateResponse getTemplate(Long templateId) {
        log.debug("Fetching template: {}", templateId);
        
        return templateRepository.findById(templateId)
                .map(this::mapToTemplateResponse)
                .orElseThrow(() -> new RuntimeException("Template not found: " + templateId));
    }

    /**
     * Запускает workflow для документа
     */
    @Transactional
    public WorkflowInstanceResponse startWorkflow(Long documentId, Long templateId, User initiatedBy) {
        log.info("Starting workflow for document: {} using template: {}", documentId, templateId);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));

        WorkflowTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new RuntimeException("Template not found: " + templateId));

        // Проверяем, что document принадлежит компании template
        if (!document.getCompany().getId().equals(template.getCompanyId())) {
            throw new RuntimeException("Document and template belong to different companies");
        }

        // Создаем workflow instance
        WorkflowInstance instance = WorkflowInstance.builder()
                .document(document)
                .template(template)
                .initiatedBy(initiatedBy)
                .build();

        instance = instanceRepository.save(instance);

        // Инициализируем workflow - создаем tasks согласно XML
        workflowEngine.initializeWorkflow(instance, template.getWorkflowXml());
        
        instance = instanceRepository.save(instance);
        log.info("Workflow instance created: {} with status: {}", instance.getId(), instance.getStatus());

        // Логируем запуск workflow
        auditService.logWorkflowStarted(instance, initiatedBy);

        // Отправляем уведомление
        eventBroadcaster.broadcastWorkflowStarted(document.getCompany().getId(), instance.getId(), documentId);

        return mapToInstanceResponse(instance);
    }

    /**
     * Получает workflow instance по ID
     */
    public WorkflowInstanceResponse getWorkflowInstance(Long instanceId) {
        log.debug("Fetching workflow instance: {}", instanceId);
        
        WorkflowInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new RuntimeException("Workflow instance not found: " + instanceId));

        return mapToInstanceResponse(instance);
    }

    /**
     * Получает текущие tasks для документа
     */
    public List<TaskResponse> getDocumentTasks(Long documentId) {
        log.debug("Fetching tasks for document: {}", documentId);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));

        List<Task> tasks = taskRepository.findByWorkflowInstanceDocument(document);
        
        return tasks.stream()
                .map(this::mapToTaskResponse)
                .collect(Collectors.toList());
    }

    /**
     * Получает pending tasks для пользователя
     */
    public List<TaskResponse> getUserPendingTasks(User user) {
        log.debug("Fetching pending tasks for user: {}", user.getEmail());

        List<Task> tasks = taskRepository.findByStatusAndRequiredRoleName(
                TaskStatus.PENDING, 
                "MANAGER" // TODO: get actual role name for user
        );

        return tasks.stream()
                .filter(task -> workflowEngine.canUserApproveTask(task, user))
                .map(this::mapToTaskResponse)
                .collect(Collectors.toList());
    }

    /**
     * Одобряет task
     */
    @Transactional
    public TaskResponse approveTask(Long taskId, User approvedBy, String comment) {
        log.info("Approving task: {} by user: {}", taskId, approvedBy.getEmail());

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

        if (!workflowEngine.canUserApproveTask(task, approvedBy)) {
            log.warn("User {} does not have permission to approve task {}", 
                approvedBy.getEmail(), taskId);
            throw new RuntimeException("User does not have required role to approve this task");
        }

        workflowEngine.approveTask(task, approvedBy, comment);
        task = taskRepository.findById(taskId).get();

        // Отправляем уведомление
        Long companyId = task.getWorkflowInstance().getDocument().getCompany().getId();
        eventBroadcaster.broadcastTaskApproved(companyId, taskId, approvedBy.getEmail());

        return mapToTaskResponse(task);
    }

    /**
     * Отклоняет task
     */
    @Transactional
    public TaskResponse rejectTask(Long taskId, User rejectedBy, String comment) {
        log.info("Rejecting task: {} by user: {}", taskId, rejectedBy.getEmail());

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

        if (!workflowEngine.canUserApproveTask(task, rejectedBy)) {
            log.warn("User {} does not have permission to reject task {}", 
                rejectedBy.getEmail(), taskId);
            throw new RuntimeException("User does not have required role to reject this task");
        }

        workflowEngine.rejectTask(task, rejectedBy, comment);
        task = taskRepository.findById(taskId).get();

        // Отправляем уведомление
        Long companyId = task.getWorkflowInstance().getDocument().getCompany().getId();
        eventBroadcaster.broadcastTaskRejected(companyId, taskId, rejectedBy.getEmail());

        return mapToTaskResponse(task);
    }

    /**
     * Получает audit историю для workflow instance
     */
    public List<WorkflowAuditLogResponse> getWorkflowAuditLog(Long instanceId) {
        log.debug("Fetching audit log for workflow instance: {}", instanceId);

        WorkflowInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new RuntimeException("Workflow instance not found: " + instanceId));

        return auditService.getWorkflowHistory(instance).stream()
                .map(log -> WorkflowAuditLogResponse.builder()
                        .id(log.getId())
                        .actionType(log.getActionType())
                        .description(log.getDescription())
                        .performedBy(log.getPerformedBy() != null ? log.getPerformedBy().getEmail() : "SYSTEM")
                        .createdAt(log.getCreatedAt())
                        .metadata(log.getMetadata())
                        .ipAddress(log.getIpAddress())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Mapper: WorkflowTemplate -> Response
     */
    private WorkflowTemplateResponse mapToTemplateResponse(WorkflowTemplate template) {
        return WorkflowTemplateResponse.builder()
                .id(template.getId())
                .name(template.getName())
                .description(template.getDescription())
                .stepsXml(template.getWorkflowXml())
                .companyId(template.getCompanyId())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }

    /**
     * Mapper: WorkflowInstance -> Response
     */
    private WorkflowInstanceResponse mapToInstanceResponse(WorkflowInstance instance) {
        return WorkflowInstanceResponse.builder()
                .id(instance.getId())
                .documentId(instance.getDocument().getId())
                .templateId(instance.getTemplate().getId())
                .status(instance.getStatus().toString())
                .tasks(taskRepository.findByWorkflowInstance(instance).stream()
                        .map(this::mapToTaskResponse)
                        .collect(Collectors.toList()))
                .startedAt(instance.getStartedAt())
                .completedAt(instance.getCompletedAt())
                .build();
    }

    /**
     * Mapper: Task -> Response
     */
    private TaskResponse mapToTaskResponse(Task task) {
        Document document = task.getWorkflowInstance().getDocument();
        return TaskResponse.builder()
                .id(task.getId())
                .stepOrder(task.getStepOrder())
                .requiredRoleName(task.getRequiredRoleName())
                .requiredRoleLevel(task.getRequiredRoleLevel())
                .status(task.getStatus().toString())
                .createdAt(task.getCreatedAt())
                .completedAt(task.getCompletedAt())
                .comment(task.getComment())
                .document(TaskResponse.DocumentInfo.builder()
                        .id(document.getId())
                        .filename(document.getOriginalFilename())
                        .build())
                .build();
    }
}
