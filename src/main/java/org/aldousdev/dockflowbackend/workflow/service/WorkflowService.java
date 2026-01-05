package org.aldousdev.dockflowbackend.workflow.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.auth.exceptions.ResourceNotFoundException;
import org.aldousdev.dockflowbackend.auth.repository.UserRepository;
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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowService {
    private final WorkflowTemplateRepository templateRepository;
    private final WorkflowInstanceRepository instanceRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
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

        Integer[] allowedRoleLevels;
        if (request.getAllowedRoleLevels() != null && ! request.getAllowedRoleLevels().isEmpty()){
            allowedRoleLevels = request.getAllowedRoleLevels().toArray(new Integer[0]);
            log.info("Setting allowed role levels: {}", Arrays.toString(allowedRoleLevels));
        }
        else{
            allowedRoleLevels = new Integer[]{100};
            log.info("Using default allowed role level: [100] (CEO only)");
        }




        WorkflowTemplate template = WorkflowTemplate.builder()
                .name(request.getName())
                .description(request.getDescription())
                .stepsXml(request.getStepsXml())
                .companyId(request.getCompanyId())
                .createdBy(createdBy)
                .isActive(true)
                .allowedRoleLevels(allowedRoleLevels)
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
    public List<WorkflowTemplateResponse> getCompanyTemplates(Long companyId, User currentUser) {
        log.debug("Fetching templates for company: {}", companyId);
        Integer userRoleLevel = currentUser.getMemberships().stream()
                .filter(m->companyId.equals(m.getCompany().getId()))
                .map(m->m.getRole().getLevel())
                .findFirst()
                .orElse(null);


        
        return templateRepository.findByCompanyIdAndIsActive(companyId, true)
                .stream()
                .map(template -> {
                    String createdByName = template.getCreatedBy() != null
                            ? template.getCreatedBy().getFirstName()
                            : "System";
                    boolean canStart = template.canStartWorkflow(userRoleLevel);
                    return WorkflowTemplateResponse.builder()
                            .id(template.getId())
                            .name(template.getName())
                            .description(template.getDescription())
                            .createdByName(createdByName)
                            .createdAt(template.getCreatedAt())
                            .isActive(template.getIsActive())
                            .canStart(canStart)
                            .build();
                })
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
        workflowEngine.initializeWorkflow(instance, template.getStepsXml());
        
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
                .orElseThrow(() -> new ResourceNotFoundException("Workflow instance not found: " + instanceId));

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
    public List<TaskResponse> getUserPendingTasks(User user, Long companyId) {
        if (user == null || companyId == null) {
            log.warn("getUserPendingTasks called with null user or companyId");
            return List.of();
        }

        log.debug("Fetching pending tasks for user: {} in company: {}", user.getEmail(), companyId);

        // Получаем уровень роли пользователя именно в этой компании
        Integer userLevel = user.getMemberships().stream()
                .filter(m -> companyId.equals(m.getCompany().getId()))
                .map(m -> m.getRole().getLevel())
                .findFirst()
                .orElse(0);

        // Берём все PENDING задачи компании через правильный JOIN
        List<Task> pendingTasks = taskRepository.findPendingTasksByCompanyId(companyId, TaskStatus.PENDING);

        // Фильтруем по правилам
        return pendingTasks.stream()
                .filter(task -> {
                    // 1. Прямое назначение
                    if (task.getAssignedTo() != null) {
                        return task.getAssignedTo().getId().equals(user.getId());
                    }
                    // 2. По уровню роли
                    return userLevel >= task.getRequiredRoleLevel();
                })
                .map(this::mapToTaskResponse)
                .toList();
    }

    /**
     * Одобряет task
     */
    @Transactional
    public TaskResponse approveTask(Long taskId, User approvedBy, String comment) {
        log.info("Approving task: {} by user: {}", taskId, approvedBy.getEmail());

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

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
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

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
                .orElseThrow(() -> new ResourceNotFoundException("Workflow instance not found: " + instanceId));

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
     * Получает все задачи компании для Kanban
     */
    public List<TaskResponse> getCompanyTasks(Long companyId) {
        log.debug("Fetching all tasks for company: {}", companyId);
        return taskRepository.findByCompanyId(companyId).stream()
                .map(this::mapToTaskResponse)
                .collect(Collectors.toList());
    }

    /**
     * Назначает задачу пользователю
     */
    @Transactional
    public TaskResponse assignTask(Long taskId, Long userId) {
        log.info("Assigning task: {} to user: {}", taskId, userId);
        
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        
        task.setAssignedTo(user);
        task = taskRepository.save(task);
        
        return mapToTaskResponse(task);
    }

    /**
     * Обновляет статус задачи
     */
    @Transactional
    public TaskResponse updateTaskStatus(Long taskId, TaskStatus status) {
        log.info("Updating task: {} status to: {}", taskId, status);
        
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        
        task.setStatus(status);
        if (status == TaskStatus.APPROVED) {
            task.setCompletedAt(LocalDateTime.now());
        }
        
        task = taskRepository.save(task);
        
        // If approved/rejected, we might need to trigger workflow engine next steps
        // For now, let's just update the status as a simple Kanban action
        
        return mapToTaskResponse(task);
    }

    /**
     * Mapper: WorkflowTemplate -> Response
     */
    private WorkflowTemplateResponse mapToTemplateResponse(WorkflowTemplate template) {
        return WorkflowTemplateResponse.builder()
                .id(template.getId())
                .name(template.getName())
                .description(template.getDescription())
                .stepsXml(template.getStepsXml())
                .companyId(template.getCompanyId())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .allowedRoleLevels(template.getAllowedRoleLevels() != null?
                        Arrays.asList(template.getAllowedRoleLevels())
                        : List.of(100))
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

    @Transactional
    public WorkflowTemplateResponse updateAllowedRoleLevels(Long templateId, List<Integer> allowedRoleLevels, User updatedBy){
        log.info("Updating allowed role levels for template {} to {}", templateId, allowedRoleLevels);
        WorkflowTemplate template = templateRepository.findById(templateId)
                .orElseThrow(()-> new ResourceNotFoundException("Template not found: " + templateId));

        if(!updatedBy.getId().equals(template.getCreatedBy().getId()) &&
        !hasHighEnoughRole(updatedBy, template.getCompanyId(),100)){
            throw new SecurityException("You are not allowed to update this template");
        }

        Integer[] newLevels = allowedRoleLevels.toArray(new Integer[0]);
        template.setAllowedRoleLevels(newLevels);

        template.setUpdatedAt(LocalDateTime.now());
        template = templateRepository.save(template);

        log.info("Permissions updated successfully");
        return mapToTemplateResponse(template);
    }

    private boolean hasHighEnoughRole(User user, Long id, int requiredLevel){
        return user.getMemberships().stream()
                .filter(m->id.equals(m.getCompany().getId()))
                .anyMatch(m->m.getRole().getLevel() >= requiredLevel);
    }
}
