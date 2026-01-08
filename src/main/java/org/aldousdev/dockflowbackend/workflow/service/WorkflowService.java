package org.aldousdev.dockflowbackend.workflow.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.auth.exceptions.ResourceNotFoundException;
import org.aldousdev.dockflowbackend.auth.repository.CompanyRoleEntityRepository;
import org.aldousdev.dockflowbackend.auth.repository.MembershipRepository;
import org.aldousdev.dockflowbackend.auth.repository.UserRepository;
import org.aldousdev.dockflowbackend.auth.entity.Membership;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.auth.service.impls.CompanyServiceImpl;
import org.aldousdev.dockflowbackend.workflow.dto.request.CreateWorkflowTemplateRequest;
import org.aldousdev.dockflowbackend.workflow.dto.request.UpdateWorkflowTemplateRequest;
import org.aldousdev.dockflowbackend.workflow.dto.request.TaskActionRequest;
import org.aldousdev.dockflowbackend.workflow.dto.response.TaskResponse;
import org.aldousdev.dockflowbackend.auth.repository.MembershipRepository;
import org.aldousdev.dockflowbackend.auth.entity.Membership;
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
    private final CompanyServiceImpl companyService;
    private final CompanyRoleEntityRepository roleRepository;
    private final MembershipRepository membershipRepository;

    /**
     * Initializes workflow for testing (delegates to WorkflowEngine)
     */
    @Transactional
    public void initializeWorkflow(WorkflowInstance instance, String workflowXml) {
        workflowEngine.initializeWorkflow(instance, workflowXml);
    }

    /**
     * Approves task for testing (delegates to WorkflowEngine)
     */
    @Transactional
    public void approveTask(Task task, User user, String comment) {
        workflowEngine.approveTask(task, user, comment);
    }

    /**
     * Rejects task for testing (delegates to WorkflowEngine)
     */
    @Transactional
    public void rejectTask(Task task, User user, String comment) {
        workflowEngine.rejectTask(task, user, comment);
    }

    /**
     * Creates new workflow template with routing rules
     */
    @Transactional
    public WorkflowTemplateResponse createTemplate(CreateWorkflowTemplateRequest request, User createdBy) {
        log.info("Creating workflow template: {} for company: {}", 
            request.getName(), request.getCompanyId());

        Integer[] allowedRoleLevels;
        if (request.getAllowedRoleLevels() != null && !request.getAllowedRoleLevels().isEmpty()){
            List<Integer> levels = new java.util.ArrayList<>(request.getAllowedRoleLevels());
            if (!levels.contains(100)) {
                levels.add(100);
            }
            allowedRoleLevels = levels.toArray(new Integer[0]);
            log.info("Setting allowed role levels (CEO included): {}", Arrays.toString(allowedRoleLevels));
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

        // Parse and save routing rules
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
     * Gets all templates for a company
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
                    WorkflowTemplateResponse response = mapToTemplateResponse(template);
                    String createdByName = template.getCreatedBy() != null
                            ? template.getCreatedBy().getFirstName()
                            : "System";
                    response.setCreatedByName(createdByName);
                    response.setCanStart(template.canStartWorkflow(userRoleLevel));
                    return response;
                })
                .collect(Collectors.toList());

    }

    /**
     * Retrieves a template by ID
     */
    public WorkflowTemplateResponse getTemplate(Long templateId) {
        log.debug("Fetching template: {}", templateId);
        
        return templateRepository.findById(templateId)
                .map(this::mapToTemplateResponse)
                .orElseThrow(() -> new RuntimeException("Template not found: " + templateId));
    }

    /**
     * Starts a workflow for a document
     */
    @Transactional
    public WorkflowInstanceResponse startWorkflow(Long documentId, Long templateId, User initiatedBy) {
        log.info("Starting workflow for document: {} using template: {}", documentId, templateId);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));

        WorkflowTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new RuntimeException("Template not found: " + templateId));

        // Check that the document belongs to the template's company
        if (!document.getCompany().getId().equals(template.getCompanyId())) {
            throw new RuntimeException("Document and template belong to different companies");
        }

        // Create workflow instance
        WorkflowInstance instance = WorkflowInstance.builder()
                .document(document)
                .template(template)
                .initiatedBy(initiatedBy)
                .build();

        instance = instanceRepository.save(instance);

        // Initialize workflow - create tasks according to XML
        workflowEngine.initializeWorkflow(instance, template.getStepsXml());
        
        instance = instanceRepository.save(instance);
        log.info("Workflow instance created: {} with status: {}", instance.getId(), instance.getStatus());

        // Log workflow start
        auditService.logWorkflowStarted(instance, initiatedBy);

        // Send notification
        eventBroadcaster.broadcastWorkflowStarted(document.getCompany().getId(), instance.getId(), documentId);

        return mapToInstanceResponse(instance);
    }

    /**
     * Retrieves a workflow instance by ID
     */
    public WorkflowInstanceResponse getWorkflowInstance(Long instanceId) {
        log.debug("Fetching workflow instance: {}", instanceId);
        
        WorkflowInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow instance not found: " + instanceId));

        return mapToInstanceResponse(instance);
    }

    /**
     * Retrieves current tasks for a document
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
     * Retrieves pending tasks for a user
     */
    public List<TaskResponse> getUserPendingTasks(User user, Long companyId) {
        if (user == null || companyId == null) {
            log.warn("getUserPendingTasks called with null user or companyId");
            return List.of();
        }

        log.debug("Fetching pending tasks for user: {} in company: {}", user.getEmail(), companyId);

        // Get the user's role level specifically in this company
        Integer userLevel = user.getMemberships().stream()
                .filter(m -> companyId.equals(m.getCompany().getId()))
                .map(m -> m.getRole().getLevel())
                .findFirst()
                .orElse(0);

        // Get all PENDING, IN_PROGRESS and CHANGES_REQUESTED tasks for the company
        List<Task> potentialTasks = taskRepository.findPendingTasksByCompanyId(companyId, TaskStatus.PENDING);
        List<Task> inProgressTasks = taskRepository.findPendingTasksByCompanyId(companyId, TaskStatus.IN_PROGRESS);
        List<Task> changesRequestedTasks = taskRepository.findPendingTasksByCompanyId(companyId, TaskStatus.CHANGES_REQUESTED);
        
        java.util.List<Task> allTasks = new java.util.ArrayList<>();
        allTasks.addAll(potentialTasks);
        allTasks.addAll(inProgressTasks);
        allTasks.addAll(changesRequestedTasks);

        // Filter by strict rules
        return allTasks.stream()
                .filter(task -> {
                    // 1. If assigned, only that user sees it (unless completed)
                    if (task.getAssignedTo() != null) {
                        return task.getAssignedTo().getId().equals(user.getId());
                    }
                    // 2. If not assigned, must match role level exactly
                    return userLevel.equals(task.getRequiredRoleLevel());
                })
                .map(this::mapToTaskResponse)
                .toList();
    }

    /**
     * Claims a task - assigns it to a user and moves to IN_PROGRESS
     */
    @Transactional
    public TaskResponse claimTask(Long taskId, User detachedUser) {
        log.info("Claiming task: {} by user: {}", taskId, detachedUser.getEmail());
        
        // Reload user to ensure it is attached
        User user = userRepository.findById(detachedUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + detachedUser.getId()));
        
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        
        if (task.getStatus() != TaskStatus.PENDING) {
            throw new RuntimeException("Task is already taken or completed");
        }

        // Check role compatibility
        // Explicitly fetch membership to ensure we have valid role data
        Long companyId = task.getWorkflowInstance().getDocument().getCompany().getId();
        Membership membership = membershipRepository.findByCompanyIdAndUserId(companyId, user.getId())
                .orElseThrow(() -> new RuntimeException("User is not a member of the company for this task"));
        
        Integer userLevel = membership.getRole().getLevel();

        log.info("Claiming task {} (req level {}): User {} (level {})", 
                taskId, task.getRequiredRoleLevel(), user.getEmail(), userLevel);

        if (userLevel < task.getRequiredRoleLevel()) {
            throw new RuntimeException("Role level mismatch: required " + 
                task.getRequiredRoleLevel() + ", but user has " + userLevel);
        }

        task.setAssignedTo(user);
        task.setStatus(TaskStatus.IN_PROGRESS);
        task = taskRepository.save(task);

        // Notify others that task is claimed
        eventBroadcaster.broadcastTaskAssigned(companyId, taskId, user.getId());
        
        return mapToTaskResponse(task);
    }

    /**
     * Executes a generic action on a task
     */
    @Transactional
    public TaskResponse executeTaskAction(Long taskId, TaskActionRequest request, User user) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        // Validate that the user is assigned to this task or has admin rights
        // simple validation: user must be the assignee
        // Validate that the user is assigned to this task or has admin rights
        boolean isAssignee = task.getAssignedTo() != null && task.getAssignedTo().getId().equals(user.getId());
        
        if (!isAssignee) {
             Long companyId = task.getWorkflowInstance().getDocument().getCompany().getId();
             Membership membership = membershipRepository.findByCompanyIdAndUserId(companyId, user.getId())
                     .orElseThrow(() -> new org.aldousdev.dockflowbackend.auth.exceptions.CompanyAccessDeniedException("You are not a member of this company context"));
             
             if (membership.getRole().getLevel() < 80) {
                 throw new org.aldousdev.dockflowbackend.auth.exceptions.CompanyAccessDeniedException("Task is not assigned to you and you don't have sufficient privileges (Level 80+) to override");
             }
        }
        
        // Also check if task is IN_PROGRESS or specifically CHANGES_REQUESTED (for resubmit)
        boolean isResubmit = request.getActionType() == org.aldousdev.dockflowbackend.workflow.enums.ActionType.RESUBMIT;
        
        if (task.getStatus() != TaskStatus.IN_PROGRESS && 
           !(task.getStatus() == TaskStatus.CHANGES_REQUESTED && isResubmit)) {
            throw new IllegalStateException("Task is not in progress (Status: " + task.getStatus() + ")");
        }
        
        // Check if the action is available for this task
        // Special case: RESUBMIT is allowed if status is CHANGES_REQUESTED (regardless of XML config)
        // Check if the action is available for this task
        // Special case: RESUBMIT is allowed if status is CHANGES_REQUESTED (regardless of XML config)
        boolean isResubmitAllowed = isResubmit && task.getStatus() == TaskStatus.CHANGES_REQUESTED;
                           
        if (!isResubmitAllowed && !task.getAvailableActions().contains(request.getActionType())) {
            throw new IllegalArgumentException("Action " + request.getActionType() + " is not available for this task");
        }

        workflowEngine.executeAction(task, request, user);
        
        return mapToTaskResponse(taskRepository.save(task));
    }

    /**
     * Approves a task
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

        // Send notification
        Long companyId = task.getWorkflowInstance().getDocument().getCompany().getId();
        eventBroadcaster.broadcastTaskApproved(companyId, taskId, approvedBy.getEmail());

        return mapToTaskResponse(task);
    }

    /**
     * Rejects a task
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

        // Send notification
        Long companyId = task.getWorkflowInstance().getDocument().getCompany().getId();
        eventBroadcaster.broadcastTaskRejected(companyId, taskId, rejectedBy.getEmail());

        return mapToTaskResponse(task);
    }

    /**
     * Retrieves audit history for a workflow instance
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
     * Retrieves all company tasks for Kanban
     */
    public List<TaskResponse> getCompanyTasks(Long companyId) {
        log.debug("Fetching all tasks for company: {}", companyId);
        return taskRepository.findByCompanyId(companyId).stream()
                .map(this::mapToTaskResponse)
                .collect(Collectors.toList());
    }

    /**
     * Assigns a task to a user
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
     * Updates task status
     */
    @Transactional
    public TaskResponse updateTaskStatus(Long taskId, TaskStatus status) {
        log.info("Updating task: {} status to: {}", taskId, status);
        
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        
        // Prevent modifying finalized tasks (Safety check)
        if (task.getStatus() == TaskStatus.APPROVED || 
            task.getStatus() == TaskStatus.REJECTED || 
            task.getStatus() == TaskStatus.CANCELLED) {
            log.warn("Attempt to modify finalized task {}: {} -> {}", taskId, task.getStatus(), status);
            throw new RuntimeException("Cannot modify a finalized task (APPROVED/REJECTED/CANCELLED)");
        }

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
    /**
     * Helper to get company roles (copied from companyService to avoid direct dependency if needed, 
     * but we already have companyService)
     */
    public java.util.List<org.aldousdev.dockflowbackend.auth.dto.response.CreateRoleResponse> getCompanyRoles(Long companyId) {
        return companyService.getAllRoles(companyId);
    }

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
                .completedByName(task.getCompletedBy() != null ? task.getCompletedBy().getFirstName() + " " + task.getCompletedBy().getLastName() : null)
                .comment(task.getComment())
                .assignedTo(task.getAssignedTo() != null ? TaskResponse.UserInfo.builder()
                        .id(task.getAssignedTo().getId())
                        .email(task.getAssignedTo().getEmail())
                        .build() : null)
                .document(TaskResponse.DocumentInfo.builder()
                        .id(document.getId())
                        .filename(document.getOriginalFilename())
                        .build())
                .document(TaskResponse.DocumentInfo.builder()
                        .id(document.getId())
                        .filename(document.getOriginalFilename())
                        .build())
                .templateId(task.getWorkflowInstance().getTemplate().getId())
                .availableActions(task.getAvailableActions().stream()
                        .map(Enum::toString)
                        .collect(Collectors.toSet()))
                .build();
    }

    @Transactional
    public WorkflowTemplateResponse updateAllowedRoleLevels(Long templateId, List<Integer> allowedRoleLevels, User updatedBy){
        log.info("Updating allowed role levels for template {} to {}", templateId, allowedRoleLevels);
        
        List<Integer> levels = new java.util.ArrayList<>(allowedRoleLevels);
        if (!levels.contains(100)) {
            levels.add(100);
        }

        WorkflowTemplate template = templateRepository.findById(templateId)
                .orElseThrow(()-> new ResourceNotFoundException("Template not found: " + templateId));

        if(!updatedBy.getId().equals(template.getCreatedBy().getId()) &&
        !hasHighEnoughRole(updatedBy, template.getCompanyId(),100)){
            throw new SecurityException("You are not allowed to update this template");
        }

        Integer[] newLevels = levels.toArray(new Integer[0]);
        template.setAllowedRoleLevels(newLevels);

        template.setUpdatedAt(LocalDateTime.now());
        template = templateRepository.save(template);

        log.info("Permissions updated successfully");
        return mapToTemplateResponse(template);
    }

    /**
     * Updates workflow template
     */
    @Transactional
    public WorkflowTemplateResponse updateTemplate(Long templateId, UpdateWorkflowTemplateRequest request, User updatedBy) {
        log.info("Updating workflow template: {} by user: {}", templateId, updatedBy.getEmail());

        WorkflowTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found: " + templateId));

        // Access check (creator or company admin)
        if (!updatedBy.getId().equals(template.getCreatedBy().getId()) &&
                !hasHighEnoughRole(updatedBy, template.getCompanyId(), 100)) {
            throw new SecurityException("You are not allowed to update this template");
        }

        if (request.getName() != null) template.setName(request.getName());
        if (request.getDescription() != null) template.setDescription(request.getDescription());
        if (request.getIsActive() != null) template.setIsActive(request.getIsActive());

        if (request.getStepsXml() != null) {
            template.setStepsXml(request.getStepsXml());
            // Re-create routing rules when XML changes
            routingRuleRepository.deleteByTemplate(template);
            try {
                WorkflowXmlParser.WorkflowDefinition definition =
                        WorkflowXmlParser.parseWorkflowDefinition(request.getStepsXml());

                for (WorkflowXmlParser.RoutingRule rule : definition.getRoutingRules()) {
                    RoutingRule routingRule = RoutingRule.builder()
                            .template(template)
                            .stepOrder(rule.getStepOrder())
                            .routingType(RoutingType.fromXmlValue(rule.getRoutingType()))
                            .targetStep(rule.getTargetStep())
                            .condition(rule.getCondition())
                            .description(rule.getDescription())
                            .isOverrideAllowed(true)
                            .build();
                    routingRuleRepository.save(routingRule);
                }
            } catch (Exception e) {
                log.warn("Could not parse routing rules during update: {}", e.getMessage());
            }
        }

        if (request.getAllowedRoleLevels() != null) {
            List<Integer> levels = new java.util.ArrayList<>(request.getAllowedRoleLevels());
            if (!levels.contains(100)) {
                levels.add(100);
            }
            template.setAllowedRoleLevels(levels.toArray(new Integer[0]));
        }

        template.setUpdatedAt(LocalDateTime.now());
        template = templateRepository.save(template);

        return mapToTemplateResponse(template);
    }

    /**
     * Deletes workflow template (soft delete)
     */
    @Transactional
    public void deleteTemplate(Long templateId, User deletedBy) {
        log.info("Deleting workflow template: {} by user: {}", templateId, deletedBy.getEmail());

        WorkflowTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found: " + templateId));

        // Access check
        if (!deletedBy.getId().equals(template.getCreatedBy().getId()) &&
                !hasHighEnoughRole(deletedBy, template.getCompanyId(), 100)) {
            throw new SecurityException("You are not allowed to delete this template");
        }

        template.setIsActive(false);
        template.setUpdatedAt(LocalDateTime.now());
        templateRepository.save(template);
    }

    private boolean hasHighEnoughRole(User user, Long id, int requiredLevel){
        return user.getMemberships().stream()
                .filter(m->id.equals(m.getCompany().getId()))
                .anyMatch(m->m.getRole().getLevel() >= requiredLevel);
    }
}
