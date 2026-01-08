package org.aldousdev.dockflowbackend.workflow.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.auth.repository.MembershipRepository;
import org.aldousdev.dockflowbackend.auth.repository.UserRepository;
import org.aldousdev.dockflowbackend.workflow.dto.request.TaskActionRequest;
import org.aldousdev.dockflowbackend.workflow.entity.Document;
import org.aldousdev.dockflowbackend.workflow.entity.RoutingRule;
import org.aldousdev.dockflowbackend.workflow.entity.Task;
import org.aldousdev.dockflowbackend.workflow.entity.WorkflowInstance;
import org.aldousdev.dockflowbackend.workflow.enums.RoutingType;
import org.aldousdev.dockflowbackend.workflow.enums.TaskStatus;
import org.aldousdev.dockflowbackend.workflow.enums.WorkFlowStatus;

import org.aldousdev.dockflowbackend.workflow.enums.ActionType;
import org.aldousdev.dockflowbackend.workflow.parser.ConditionEvaluator;
import org.aldousdev.dockflowbackend.workflow.event.WorkflowEventBroadcaster;
import org.aldousdev.dockflowbackend.workflow.parser.WorkflowXmlParser;
import org.aldousdev.dockflowbackend.workflow.repository.RoutingRuleRepository;
import org.aldousdev.dockflowbackend.workflow.repository.TaskRepository;
import org.aldousdev.dockflowbackend.workflow.service.EmailNotificationService;
import org.aldousdev.dockflowbackend.workflow.service.WorkflowAuditService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkflowEngine {
    private final TaskRepository taskRepository;
    private final RoutingRuleRepository routingRuleRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final WorkflowEventBroadcaster eventBroadcaster;
    private final WorkflowAuditService auditService;
    private final EmailNotificationService emailNotificationService;

    /**
     * Initializes workflow - creates tasks ONLY for the first step
     */
    @Transactional
    public void initializeWorkflow(WorkflowInstance workflowInstance, String workflowXml) {
        log.info("Initializing workflow for document: {}", workflowInstance.getDocument().getId());

        try {
            List<WorkflowXmlParser.WorkflowStep> steps = WorkflowXmlParser.parseWorkflowSteps(workflowXml);

            // Group steps by order
            Map<Integer, List<WorkflowXmlParser.WorkflowStep>> groupedSteps =
                    steps.stream().collect(Collectors.groupingBy(WorkflowXmlParser.WorkflowStep::getOrder));

            // Finding the minimum (first) step
            Integer firstStepOrder = groupedSteps.keySet().stream()
                    .min(Integer::compareTo)
                    .orElseThrow(() -> new RuntimeException("No steps found in workflow XML"));

            log.info("Creating tasks only for first step: {}", firstStepOrder);

            // Create tasks ONLY for the first step
            List<WorkflowXmlParser.WorkflowStep> firstStepGroup = groupedSteps.get(firstStepOrder);

            // Check if there are parallel steps
            boolean hasParallelSteps = firstStepGroup.stream()
                    .anyMatch(WorkflowXmlParser.WorkflowStep::isParallel);

            if (hasParallelSteps) {
                // Create parallel tasks for all users with corresponding roles
                createParallelTasks(workflowInstance, firstStepOrder, firstStepGroup);
            } else {
                // Normal sequential execution
                for (WorkflowXmlParser.WorkflowStep step : firstStepGroup) {
                    createTask(workflowInstance, firstStepOrder, step);
                }
            }

            workflowInstance.setStatus(WorkFlowStatus.IN_PROGRESS);
            log.info("Workflow initialized with first step {} (total {} steps in workflow)",
                    firstStepOrder, groupedSteps.size());

        } catch (Exception e) {
            log.error("Error initializing workflow", e);
            workflowInstance.setStatus(WorkFlowStatus.REJECTED);
            throw new RuntimeException("Failed to initialize workflow: " + e.getMessage());
        }
    }

    /**
     * Creates a task for a specific step
     */
    private void createTask(WorkflowInstance instance, Integer stepOrder,
                           WorkflowXmlParser.WorkflowStep step) {
        log.debug("Creating task for step {} - role {}", stepOrder, step.getRoleName());

        // Calculate available actions first
        java.util.Set<ActionType> actions = new java.util.HashSet<>();
        actions.add(ActionType.APPROVE);
        actions.add(ActionType.REJECT);
        
        if (step.getAllowedActions() != null) {
            for (String actionStr : step.getAllowedActions()) {
                try {
                    actions.add(ActionType.valueOf(actionStr.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid action type in XML: {}", actionStr);
                }
            }
        }

        Task task = Task.builder()
                .workflowInstance(instance)
                .stepOrder(stepOrder)
                .requiredRoleName(step.getRoleName())
                .requiredRoleLevel(step.getRoleLevel())
                .status(TaskStatus.PENDING)
                .assignedBy(instance.getInitiatedBy())
                .availableActions(actions)
                .build();

        taskRepository.save(task);

        // Log task creation
        auditService.logTaskCreated(task, step.getRoleName());

        // Send notification about new task creation
        Long companyId = instance.getDocument().getCompany().getId();
        eventBroadcaster.broadcastTaskCreated(companyId, task.getId(), step.getRoleName());
    }

    /**
     * Creates parallel tasks for all users with corresponding roles
     */
    private void createParallelTasks(WorkflowInstance instance, Integer stepOrder,
                                   List<WorkflowXmlParser.WorkflowStep> parallelSteps) {
        log.info("Creating parallel tasks for step {} with {} parallel roles",
                stepOrder, parallelSteps.size());

        // Group steps by role (in case of duplicate roles)
        Map<String, WorkflowXmlParser.WorkflowStep> stepsByRole = parallelSteps.stream()
            .collect(Collectors.toMap(
                WorkflowXmlParser.WorkflowStep::getRoleName,
                step -> step,
                (existing, replacement) -> existing // take the first one if duplicated
            ));

        // For each role, create a task for all users with that role
        for (Map.Entry<String, WorkflowXmlParser.WorkflowStep> entry : stepsByRole.entrySet()) {
            String roleName = entry.getKey();
            WorkflowXmlParser.WorkflowStep step = entry.getValue();

            log.debug("Creating parallel tasks for role {} at step {}", roleName, stepOrder);

            // Get all company users with the required role
            List<User> usersWithRole = getUsersWithRoleInCompany(
                roleName,
                step.getRoleLevel(),
                instance.getDocument().getCompany().getId()
            );

            if (usersWithRole.isEmpty()) {
                log.warn("No users found with role {} and level >= {} in company {}",
                        roleName, step.getRoleLevel(), instance.getDocument().getCompany().getId());
                // Create a task without a specific assignee
                createTask(instance, stepOrder, step);
            } else {
                // Create a task for each user with this role
                for (User user : usersWithRole) {
                    createTaskForSpecificUser(instance, stepOrder, step, user);
                }
            }
        }
    }

    /**
     * Creates a task for a specific user
     */
    private void createTaskForSpecificUser(WorkflowInstance instance, Integer stepOrder,
                                         WorkflowXmlParser.WorkflowStep step, User assignedUser) {
        log.debug("Creating task for user {} at step {}", assignedUser.getEmail(), stepOrder);

        // Calculate available actions first
        java.util.Set<ActionType> actions = new java.util.HashSet<>();
        actions.add(ActionType.APPROVE);
        actions.add(ActionType.REJECT);
        
        if (step.getAllowedActions() != null) {
            for (String actionStr : step.getAllowedActions()) {
                try {
                    actions.add(ActionType.valueOf(actionStr.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid action type in XML: {}", actionStr);
                }
            }
        }

        Task task = Task.builder()
                .workflowInstance(instance)
                .stepOrder(stepOrder)
                .requiredRoleName(step.getRoleName())
                .requiredRoleLevel(step.getRoleLevel())
                .status(TaskStatus.PENDING)
                .assignedBy(instance.getInitiatedBy())
                .assignedTo(assignedUser) // Specific assignment
                .availableActions(actions)
                .build();



        taskRepository.save(task);

        // Log task creation
        auditService.logTaskCreated(task, step.getRoleName());

        // Send notification to the specific user
        Long companyId = instance.getDocument().getCompany().getId();
        eventBroadcaster.broadcastTaskCreated(companyId, task.getId(), step.getRoleName());
        eventBroadcaster.broadcastTaskAssigned(companyId, task.getId(), assignedUser.getId());
    }

    /**
     * Gets all company users with the specified role and level
     */
    private List<User> getUsersWithRoleInCompany(String roleName, Integer minRoleLevel, Long companyId) {
        log.debug("Finding users with role {} and level >= {} in company {}",
                roleName, minRoleLevel, companyId);

        return membershipRepository.findUsersByCompanyIdAndRoleNameAndMinLevel(
            companyId, roleName, minRoleLevel);
    }

    /**
     * Approve a task and move to the next step
     */
    @Transactional
    public void approveTask(Task task, User approvedBy, String comment) {
        log.info("Approving task: {} by user: {}", task.getId(), approvedBy.getEmail());

        // Check that the task is still PENDING or IN_PROGRESS
        if (task.getStatus() != TaskStatus.PENDING && task.getStatus() != TaskStatus.IN_PROGRESS) {
            log.warn("Task {} is already {}, cannot approve", task.getId(), task.getStatus());
            throw new RuntimeException("Task is already " + task.getStatus());
        }

        task.setStatus(TaskStatus.APPROVED);
        task.setCompletedBy(approvedBy);
        task.setCompletedAt(LocalDateTime.now());
        task.setComment(comment);
        taskRepository.save(task);

        log.info("Task {} marked as APPROVED", task.getId());

        // Log approval
        auditService.logTaskApproved(task, approvedBy, comment);

        // Send email notification
        emailNotificationService.notifyTaskApproved(task, approvedBy);

            // Determine next step based on conditions
            determineAndMoveToNextStep(task.getWorkflowInstance(), task, true);
        }

    /**
     * Reject a task
     */
    @Transactional
    public void rejectTask(Task task, User rejectedBy, String comment) {
        log.info("Rejecting task: {} by user: {}", task.getId(), rejectedBy.getEmail());
        
        task.setStatus(TaskStatus.REJECTED);
        task.setCompletedAt(LocalDateTime.now());
        task.setCompletedBy(rejectedBy);
        task.setComment(comment);
        taskRepository.save(task);

        // Notify
        Long companyId = task.getWorkflowInstance().getDocument().getCompany().getId();
        eventBroadcaster.broadcastTaskRejected(companyId, task.getId(), String.valueOf(rejectedBy.getId()));
        
        // Handle routing based on rejection (e.g. stop workflow or go to specific step)
        handleRouting(task, RoutingType.ON_REJECT);
    }

    private void handleRouting(Task task, RoutingType routingType) {
        WorkflowInstance instance = task.getWorkflowInstance();

        // Look for routing rule for this step and ON_REJECT type
        java.util.Optional<RoutingRule> rule = routingRuleRepository
                .findByTemplateAndStepOrderAndRoutingType(
                        instance.getTemplate(),
                        task.getStepOrder(),
                        routingType
                );

        if (rule.isPresent()) {
            RoutingRule routingRule = rule.get();
            Integer targetStep = routingRule.getTargetStep();
            
            log.info("Applying routing rule: step {} -> targetStep {}", 
                task.getStepOrder(), targetStep);

            if (targetStep == null) {
               // Logic to complete/reject workflow handled in caller or determineNextStep
               // But here rejectTask calls handleRouting which was missing.
               // Re-implementing logic from deleted duplicate rejectTask:
               
                // Complete workflow as rejected
                instance.setStatus(WorkFlowStatus.REJECTED);
                instance.setCompletedAt(LocalDateTime.now());
                log.info("Workflow rejected: {}", instance.getId());
                
                // Log workflow rejection
                auditService.logWorkflowRejected(instance, task.getComment());
                
                // Send email notification
                emailNotificationService.notifyWorkflowRejected(instance, task.getComment());
                
                Long companyId = instance.getDocument().getCompany().getId();
                eventBroadcaster.broadcastWorkflowRejected(companyId, instance.getId(), task.getComment());
            } else {
                 // Use conditional routing to return to a step
                 determineAndMoveToNextStep(instance, task, false);
            }
        } else {
             // Use conditional routing for reject
             determineAndMoveToNextStep(instance, task, false);
        }
    }

    /**
     * Executes a generic action (Delegate, Hold, etc.)
     */
    @Transactional
    public void executeAction(Task task, TaskActionRequest request, User actor) {
        log.info("Executing action {} on task {}", request.getActionType(), task.getId());
        
        switch (request.getActionType()) {
            case DELEGATE:
                handleDelegate(task, request, actor);
                break;
            case REQUEST_CHANGES:
                handleRequestChanges(task, request, actor);
                break;
            case HOLD:
                handleHold(task, request, actor);
                break;
            case RESUBMIT:
                handleResubmit(task, request, actor);
                break;
            default:
                throw new IllegalArgumentException("Unsupported action type: " + request.getActionType());
        }
    }

    private void handleResubmit(Task task, TaskActionRequest request, User actor) {
        // Reset to PENDING and clear assignment so it goes back to pool
        task.setStatus(TaskStatus.PENDING);
        task.setAssignedTo(null);
        task.setComment(request.getComment());
        taskRepository.save(task);

        auditService.logTaskAction(task, ActionType.RESUBMIT, actor, "Resubmitted logic: " + request.getComment());
        
        // Notify role group that task is available again
        Long companyId = task.getWorkflowInstance().getDocument().getCompany().getId();
        eventBroadcaster.broadcastTaskCreated(companyId, task.getId(), task.getRequiredRoleName());
    }

    private void handleDelegate(Task task, TaskActionRequest request, User actor) {
        if (request.getTargetUserId() == null) {
            throw new IllegalArgumentException("Target user ID is required for delegation");
        }
        
        User targetUser = userRepository.findById(request.getTargetUserId())
            .orElseThrow(() -> new IllegalArgumentException("Target user not found"));
            
        // Reassign
        task.setAssignedTo(targetUser);
        // We keep status IN_PROGRESS so the new user sees it
        // Or we could reset to PENDING if we want them to "Claim" it? 
        // Let's assume direct assignment -> IN_PROGRESS for that user.
        taskRepository.save(task);
        
        auditService.logTaskAction(task, ActionType.DELEGATE, actor, 
            "Delegated to " + targetUser.getEmail() + ". Comment: " + request.getComment());
            
        // Notify new assignee
        eventBroadcaster.broadcastTaskAssigned(
            task.getWorkflowInstance().getDocument().getCompany().getId(), 
            task.getId(), 
            targetUser.getId()
        );
    }

    private void handleRequestChanges(Task task, TaskActionRequest request, User actor) {
        // 1. Update status
        task.setStatus(TaskStatus.CHANGES_REQUESTED);
        task.setComment(request.getComment());
        
        // 2. Reassign to Initiator
        User initiator = task.getWorkflowInstance().getInitiatedBy();
        if (initiator != null) {
            task.setAssignedTo(initiator);
            log.info("Task {} reassigned to initiator {} for changes", task.getId(), initiator.getEmail());
        } else {
            log.warn("No initiator found for workflow instance {}, task {} remains assigned to current user", 
                     task.getWorkflowInstance().getId(), task.getId());
        }

        taskRepository.save(task);
        
        // 3. Log action
        auditService.logTaskAction(task, ActionType.REQUEST_CHANGES, actor, request.getComment());
        
        // 4. Notify Initiator
        if (initiator != null) {
            Long companyId = task.getWorkflowInstance().getDocument().getCompany().getId();
            eventBroadcaster.broadcastTaskAssigned(companyId, task.getId(), initiator.getId());
        }
    }

    private void handleHold(Task task, TaskActionRequest request, User actor) {
        task.setStatus(TaskStatus.ON_HOLD);
        task.setComment(request.getComment());
        taskRepository.save(task);
        
        auditService.logTaskAction(task, ActionType.HOLD, actor, request.getComment());
    }



    /**
     * Return workflow to a specific step
     */
    @Transactional
    protected void returnToStep(WorkflowInstance instance, Integer fromStep, Integer targetStep) {
        log.info("Returning workflow {} from step {} to step {}", instance.getId(), fromStep, targetStep);

        List<Task> tasks = taskRepository.findByWorkflowInstance(instance);

        // Mark all tasks of current step and after as CANCELLED
        tasks.stream()
                .filter(t -> t.getStepOrder() >= targetStep)
                .forEach(t -> {
                    t.setStatus(TaskStatus.CANCELLED);
                    taskRepository.save(t);
                    
                    // Log task cancellation
                    auditService.logTaskCancelled(t, "Workflow returned to step " + targetStep);
                });

        // Create new tasks for targetStep (only if they don't exist yet)
        List<Task> targetStepTasks = tasks.stream()
                .filter(t -> t.getStepOrder().equals(targetStep))
                .toList();

        if (targetStepTasks.isEmpty()) {
            log.warn("No tasks found for target step {}, skipping recreation", targetStep);
        } else {
            // Restore task status on target step
            targetStepTasks.forEach(t -> {
                t.setStatus(TaskStatus.PENDING);
                t.setCompletedBy(null);
                t.setCompletedAt(null);
                taskRepository.save(t);
                
                log.info("Task {} reset to PENDING", t.getId());
            });
        }

        instance.setStatus(WorkFlowStatus.IN_PROGRESS);
        
        // Log routing rule application
        auditService.logRoutingRuleApplied(instance, fromStep, targetStep);
    }

    /**
     * Move workflow to the next step
     */
    @Transactional
    public void moveToNextStep(WorkflowInstance instance) {
        log.info("Moving to next step in workflow: {}", instance.getId());

        List<Task> tasks = taskRepository.findByWorkflowInstance(instance);
        
        // Find current step
        Integer currentStep = tasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.PENDING || t.getStatus() == TaskStatus.IN_PROGRESS)
                .map(Task::getStepOrder)
                .min(Integer::compareTo)
                .orElse(null);

        if (currentStep == null) {
            // All tasks completed
            instance.setStatus(WorkFlowStatus.COMPLETED);
            instance.setCompletedAt(LocalDateTime.now());
            log.info("Workflow completed: {}", instance.getId());
            
            // Log completion
            auditService.logWorkflowCompleted(instance);
            
            // Send email notification
            emailNotificationService.notifyWorkflowCompleted(instance);
            
            // Send completion notification
            Long companyId = instance.getDocument().getCompany().getId();
            eventBroadcaster.broadcastWorkflowCompleted(companyId, instance.getId());
            return;
        }

        // Check if all tasks of current step are completed
        List<Task> currentStepTasks = tasks.stream()
                .filter(t -> t.getStepOrder().equals(currentStep))
                .toList();

        boolean allApproved = currentStepTasks.stream()
                .allMatch(t -> t.getStatus() == TaskStatus.APPROVED);

        if (allApproved) {
            log.info("Step {} completed, moving to next", currentStep);
        }
    }

    /**
     * Determines next workflow step based on conditions
     */
    public Integer determineNextStep(WorkflowInstance instance, Task completedTask,
                                   boolean wasApproved, String workflowXml) {
        log.info("Determining next step for workflow {} after task {} ({})",
                instance.getId(), completedTask.getId(), wasApproved ? "approved" : "rejected");

        try {
            Document document = instance.getDocument();

            // Find suitable routing rule
            RoutingType routingType = wasApproved ? RoutingType.ON_APPROVE : RoutingType.ON_REJECT;

            RoutingRule applicableRule = routingRuleRepository
                .findByTemplateAndStepOrderAndRoutingType(
                    instance.getTemplate(),
                    completedTask.getStepOrder(),
                    routingType
                )
                .filter(rule -> {
                    // Check condition if present
                    String condition = rule.getCondition();
                    return condition == null || ConditionEvaluator.evaluate(condition, document);
                })
                .orElse(null);

            if (applicableRule != null) {
                Integer targetStep = applicableRule.getTargetStep();
                log.info("Found routing rule: step {} -> targetStep {} (condition: {})",
                        completedTask.getStepOrder(), targetStep, applicableRule.getCondition());

                if (targetStep == null) {
                    // Complete workflow
                    log.info("Routing rule indicates workflow completion");
                    return null;
                }

                return targetStep;
            }

            // If no suitable rule, use standard logic
            return getNextSequentialStep(instance, completedTask.getStepOrder(), workflowXml);

        } catch (Exception e) {
            log.error("Error determining next step: {}", e.getMessage(), e);
            // In case of error, return next sequential step
            return getNextSequentialStep(instance, completedTask.getStepOrder(), workflowXml);
        }
    }

    /**
     * Gets next sequential step (for fallback logic)
     */
    private Integer getNextSequentialStep(WorkflowInstance instance, Integer currentStep, String workflowXml) {
        try {
            WorkflowXmlParser.WorkflowDefinition definition =
                WorkflowXmlParser.parseWorkflowDefinition(workflowXml);

            // Find all orders that are HIGHER than currentStep
            List<Integer> higherOrders = definition.getSteps().stream()
                .map(WorkflowXmlParser.WorkflowStep::getOrder)
                .filter(order -> order > currentStep)
                .distinct()
                .sorted()
                .toList();

            // If found higher orders, return the smallest one (sequential next)
            if (!higherOrders.isEmpty()) {
                return higherOrders.get(0);
            }

            // This is the last step - complete workflow
            return null;

        } catch (Exception e) {
            log.error("Error getting next sequential step: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Determines next step and moves workflow
     */
    @Transactional
    public void determineAndMoveToNextStep(WorkflowInstance instance, Task completedTask, boolean wasApproved) {
        log.info("Determining and moving to next step after task {} ({})",
                completedTask.getId(), wasApproved ? "approved" : "rejected");

        try {
            // Check if all tasks of current step are completed
            List<Task> currentStepTasks = taskRepository.findByWorkflowInstance(instance).stream()
                    .filter(t -> t.getStepOrder().equals(completedTask.getStepOrder()))
                    .toList();

            long blockingCount = currentStepTasks.stream()
                    .filter(t -> t.getStatus() == TaskStatus.PENDING || t.getStatus() == TaskStatus.IN_PROGRESS)
                    .count();

            if (blockingCount > 0) {
                log.info("Step {} still has {} blocking tasks (PENDING/IN_PROGRESS), waiting for completion",
                        completedTask.getStepOrder(), blockingCount);
                return; // Wait for all current step tasks to complete
            }

            log.info("All tasks for step {} completed, determining next step",
                    completedTask.getStepOrder());

            String workflowXml = instance.getTemplate().getStepsXml();
            Integer nextStep = determineNextStep(instance, completedTask, wasApproved, workflowXml);

            if (nextStep == null) {
                // Workflow completed
                completeWorkflow(instance, wasApproved);
            } else if (nextStep.equals(completedTask.getStepOrder())) {
                // Return to the same step - reset tasks
                log.info("Returning to the same step {}, resetting tasks", nextStep);
                returnToStep(instance, completedTask.getStepOrder(), nextStep);
            } else {
                // Create tasks for next step
                createTasksForStep(instance, nextStep, workflowXml);

                // Send WebSocket notifications to next step participants
                notifyNextStepParticipants(instance, nextStep, workflowXml);
            }

        } catch (Exception e) {
            log.error("Error in determineAndMoveToNextStep: {}", e.getMessage(), e);
            // Fallback: complete workflow
            completeWorkflow(instance, false);
        }
    }

    /**
     * Completes workflow
     */
    private void completeWorkflow(WorkflowInstance instance, boolean successful) {
        instance.setStatus(successful ? WorkFlowStatus.COMPLETED : WorkFlowStatus.REJECTED);
        instance.setCompletedAt(LocalDateTime.now());

        log.info("Workflow {} completed ({})", instance.getId(),
                successful ? "successfully" : "with rejection");

        // Log completion
        if (successful) {
            auditService.logWorkflowCompleted(instance);
            emailNotificationService.notifyWorkflowCompleted(instance);
        } else {
            auditService.logWorkflowRejected(instance, "Completed via conditional routing");
            emailNotificationService.notifyWorkflowRejected(instance, "Completed via conditional routing");
        }

        // Send WebSocket notification
        Long companyId = instance.getDocument().getCompany().getId();
        if (successful) {
            eventBroadcaster.broadcastWorkflowCompleted(companyId, instance.getId());
        } else {
            eventBroadcaster.broadcastWorkflowRejected(companyId, instance.getId(),
                "Completed via conditional routing");
        }
    }

    /**
     * Notifies participants of the next step
     */
    private void notifyNextStepParticipants(WorkflowInstance instance, Integer nextStep, String workflowXml) {
        try {
            WorkflowXmlParser.WorkflowDefinition definition =
                WorkflowXmlParser.parseWorkflowDefinition(workflowXml);

            // Find roles for next step
            List<String> rolesForNextStep = definition.getSteps().stream()
                .filter(step -> step.getOrder().equals(nextStep))
                .map(WorkflowXmlParser.WorkflowStep::getRoleName)
                .distinct()
                .toList();

            // Send notifications to all users with these roles
            Long companyId = instance.getDocument().getCompany().getId();
            for (String roleName : rolesForNextStep) {
                eventBroadcaster.broadcastTaskCreated(companyId, null, roleName); // null taskId for generic notification
            }

        } catch (Exception e) {
            log.error("Error notifying next step participants: {}", e.getMessage());
        }
    }

    /**
     * Creates tasks for specified step
     */
    public void createTasksForStep(WorkflowInstance instance, Integer stepOrder, String workflowXml) {
        log.info("Creating tasks for step {} in workflow {}", stepOrder, instance.getId());

        try {
            // Check existing tasks for this step
            List<Task> existingTasks = taskRepository.findByWorkflowInstance(instance).stream()
                    .filter(t -> t.getStepOrder().equals(stepOrder))
                    .toList();

            if (!existingTasks.isEmpty()) {
                log.info("Found {} existing tasks for step {}, checking their status",
                        existingTasks.size(), stepOrder);

                // If there are PENDING tasks - don't create new
                boolean hasPendingTasks = existingTasks.stream()
                        .anyMatch(t -> t.getStatus() == TaskStatus.PENDING);

                if (hasPendingTasks) {
                    log.info("PENDING tasks already exist for step {}, skipping creation", stepOrder);
                    return;
                }

                // If all tasks are completed (APPROVED/REJECTED/CANCELLED)
                // This could be a return to step - then create new tasks
                log.info("All existing tasks for step {} are completed, will create new tasks", stepOrder);
            }

            WorkflowXmlParser.WorkflowDefinition definition =
                    WorkflowXmlParser.parseWorkflowDefinition(workflowXml);

            // Find steps with specified order
            List<WorkflowXmlParser.WorkflowStep> stepsForOrder = definition.getSteps().stream()
                    .filter(step -> step.getOrder().equals(stepOrder))
                    .toList();

            if (stepsForOrder.isEmpty()) {
                log.warn("No steps found for order {} in workflow {}", stepOrder, instance.getId());
                return;
            }

            // Check if there are parallel steps
            boolean hasParallelSteps = stepsForOrder.stream()
                    .anyMatch(WorkflowXmlParser.WorkflowStep::isParallel);

            if (hasParallelSteps) {
                // Create parallel tasks
                createParallelTasks(instance, stepOrder, stepsForOrder);
            } else {
                // Create normal tasks (for compatibility)
                for (WorkflowXmlParser.WorkflowStep step : stepsForOrder) {
                    createTask(instance, stepOrder, step);
                }
            }

            log.info("Successfully created tasks for step {}", stepOrder);

        } catch (Exception e) {
            log.error("Error creating tasks for step {}: {}", stepOrder, e.getMessage(), e);
        }
    }

    /**
     * Checks if user can approve task
     */
    public boolean canUserApproveTask(Task task, User user) {
        // Get user role in company
        var company = task.getWorkflowInstance().getDocument().getCompany();

        var userRole = user.getMemberships().stream()
                .filter(m -> m.getCompany().getId().equals(company.getId()))
                .map(m -> m.getRole().getLevel())
                .findFirst()
                .orElse(0);

        // Strict role matching: user level must match task level exactly
        // to avoid "higher roles doing lower roles work" unless claimed or delegated.
        boolean levelMatches = userRole.equals(task.getRequiredRoleLevel());
        
        // If task is already assigned to someone, ONLY that person can approve
        if (task.getAssignedTo() != null) {
            return task.getAssignedTo().getId().equals(user.getId());
        }

        // CEO with level 100 should not be doing worker tasks unless specifically assigned.
        // We remove the hardcoded 100 bypass to promote order.
        
        return levelMatches;
    }
}
