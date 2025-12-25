package org.aldousdev.dockflowbackend.workflow.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.workflow.entity.RoutingRule;
import org.aldousdev.dockflowbackend.workflow.entity.Task;
import org.aldousdev.dockflowbackend.workflow.entity.WorkflowInstance;
import org.aldousdev.dockflowbackend.workflow.enums.RoutingType;
import org.aldousdev.dockflowbackend.workflow.enums.TaskStatus;
import org.aldousdev.dockflowbackend.workflow.enums.WorkFlowStatus;
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
    private final WorkflowEventBroadcaster eventBroadcaster;
    private final WorkflowAuditService auditService;
    private final EmailNotificationService emailNotificationService;

    /**
     * Инициализирует workflow - создает tasks для всех шагов
     */
    @Transactional
    public void initializeWorkflow(WorkflowInstance workflowInstance, String workflowXml) {
        log.info("Initializing workflow for document: {}", workflowInstance.getDocument().getId());
        
        try {
            List<WorkflowXmlParser.WorkflowStep> steps = WorkflowXmlParser.parseWorkflowSteps(workflowXml);
            
            // Группируем параллельные шаги
            Map<Integer, List<WorkflowXmlParser.WorkflowStep>> groupedSteps = 
                steps.stream().collect(Collectors.groupingBy(WorkflowXmlParser.WorkflowStep::getOrder));

            for (Map.Entry<Integer, List<WorkflowXmlParser.WorkflowStep>> entry : groupedSteps.entrySet()) {
                Integer stepOrder = entry.getKey();
                List<WorkflowXmlParser.WorkflowStep> stepGroup = entry.getValue();

                // Для каждого шага (или группы параллельных шагов) создаем task
                for (WorkflowXmlParser.WorkflowStep step : stepGroup) {
                    createTask(workflowInstance, stepOrder, step);
                }
            }

            workflowInstance.setStatus(WorkFlowStatus.IN_PROGRESS);
            log.info("Workflow initialized with {} tasks", steps.size());

        } catch (Exception e) {
            log.error("Error initializing workflow", e);
            workflowInstance.setStatus(WorkFlowStatus.REJECTED);
            throw new RuntimeException("Failed to initialize workflow: " + e.getMessage());
        }
    }

    /**
     * Создает task для конкретного шага
     */
    private void createTask(WorkflowInstance instance, Integer stepOrder, 
                           WorkflowXmlParser.WorkflowStep step) {
        log.debug("Creating task for step {} - role {}", stepOrder, step.getRoleName());

        Task task = Task.builder()
                .workflowInstance(instance)
                .stepOrder(stepOrder)
                .requiredRoleName(step.getRoleName())
                .requiredRoleLevel(step.getRoleLevel())
                .status(TaskStatus.PENDING)
                .assignedBy(instance.getInitiatedBy())
                .build();

        taskRepository.save(task);
        
        // Логируем создание task
        auditService.logTaskCreated(task, step.getRoleName());
        
        // Отправляем уведомление о создании новой task
        Long companyId = instance.getDocument().getCompany().getId();
        eventBroadcaster.broadcastTaskCreated(companyId, task.getId(), step.getRoleName());
    }

    /**
     * Одобрить task и переместить на следующий шаг
     */
    @Transactional
    public void approveTask(Task task, User approvedBy, String comment) {
        log.info("Approving task: {} by user: {}", task.getId(), approvedBy.getEmail());

        task.setStatus(TaskStatus.APPROVED);
        task.setCompletedBy(approvedBy);
        task.setCompletedAt(LocalDateTime.now());
        task.setComment(comment);
        taskRepository.save(task);

        // Логируем одобрение
        auditService.logTaskApproved(task, approvedBy, comment);
        
        // Отправляем email уведомление
        emailNotificationService.notifyTaskApproved(task, approvedBy);

        // Проверяем, можно ли переместить на следующий шаг
        moveToNextStep(task.getWorkflowInstance());
    }

    /**
     * Отклонить task с применением правил маршрутизации
     */
    @Transactional
    public void rejectTask(Task task, User rejectedBy, String comment) {
        log.info("Rejecting task: {} by user: {}", task.getId(), rejectedBy.getEmail());

        task.setStatus(TaskStatus.REJECTED);
        task.setCompletedBy(rejectedBy);
        task.setCompletedAt(LocalDateTime.now());
        task.setComment(comment);
        taskRepository.save(task);

        // Логируем отклонение
        auditService.logTaskRejected(task, rejectedBy, comment);
        
        // Отправляем email уведомление
        emailNotificationService.notifyTaskRejected(task, rejectedBy, comment);

        WorkflowInstance instance = task.getWorkflowInstance();

        // Ищем правило маршрутизации для этого шага и типа ON_REJECT
        java.util.Optional<RoutingRule> rule = routingRuleRepository
                .findByTemplateAndStepOrderAndRoutingType(
                        instance.getTemplate(),
                        task.getStepOrder(),
                        RoutingType.ON_REJECT
                );

        if (rule.isPresent()) {
            RoutingRule routingRule = rule.get();
            Integer targetStep = routingRule.getTargetStep();

            log.info("Applying routing rule: step {} -> targetStep {}", 
                task.getStepOrder(), targetStep);

            if (targetStep == null) {
                // Завершить workflow как отклонено
                instance.setStatus(WorkFlowStatus.REJECTED);
                instance.setCompletedAt(LocalDateTime.now());
                log.info("Workflow rejected: {}", instance.getId());
                
                // Логируем отклонение workflow
                auditService.logWorkflowRejected(instance, comment);
                
                // Отправляем email уведомление
                emailNotificationService.notifyWorkflowRejected(instance, comment);
                
                Long companyId = instance.getDocument().getCompany().getId();
                eventBroadcaster.broadcastWorkflowRejected(companyId, instance.getId(), comment);
            } else {
                // Вернуться на шаг targetStep
                returnToStep(instance, task.getStepOrder(), targetStep);
                
                Long companyId = instance.getDocument().getCompany().getId();
                eventBroadcaster.broadcastWorkflowRejected(companyId, instance.getId(), 
                    "Returned to step " + targetStep);
            }
        } else {
            // Нет правила - просто отклонить workflow
            instance.setStatus(WorkFlowStatus.REJECTED);
            instance.setCompletedAt(LocalDateTime.now());
            log.info("Workflow rejected (no routing rule): {}", instance.getId());
            
            // Логируем отклонение workflow
            auditService.logWorkflowRejected(instance, comment);
            
            // Отправляем email уведомление
            emailNotificationService.notifyWorkflowRejected(instance, comment);
            
            Long companyId = instance.getDocument().getCompany().getId();
            eventBroadcaster.broadcastWorkflowRejected(companyId, instance.getId(), comment);
        }
    }

    /**
     * Вернуть workflow на конкретный шаг
     */
    @Transactional
    protected void returnToStep(WorkflowInstance instance, Integer fromStep, Integer targetStep) {
        log.info("Returning workflow {} from step {} to step {}", instance.getId(), fromStep, targetStep);

        List<Task> tasks = taskRepository.findByWorkflowInstance(instance);

        // Отмечаем все tasks текущего шага и после как CANCELLED
        tasks.stream()
                .filter(t -> t.getStepOrder() >= targetStep)
                .forEach(t -> {
                    t.setStatus(TaskStatus.CANCELLED);
                    taskRepository.save(t);
                    
                    // Логируем отмену task
                    auditService.logTaskCancelled(t, "Workflow returned to step " + targetStep);
                });

        // Создаем новые tasks для targetStep (только если их еще нет)
        List<Task> targetStepTasks = tasks.stream()
                .filter(t -> t.getStepOrder().equals(targetStep))
                .toList();

        if (targetStepTasks.isEmpty()) {
            log.warn("No tasks found for target step {}, skipping recreation", targetStep);
        } else {
            // Восстанавливаем статус задач на целевом шаге
            targetStepTasks.forEach(t -> {
                t.setStatus(TaskStatus.PENDING);
                t.setCompletedBy(null);
                t.setCompletedAt(null);
                taskRepository.save(t);
                
                log.info("Task {} reset to PENDING", t.getId());
            });
        }

        instance.setStatus(WorkFlowStatus.IN_PROGRESS);
        
        // Логируем применение правила маршрутизации
        auditService.logRoutingRuleApplied(instance, fromStep, targetStep);
    }

    /**
     * Перемещает workflow на следующий шаг
     */
    @Transactional
    public void moveToNextStep(WorkflowInstance instance) {
        log.info("Moving to next step in workflow: {}", instance.getId());

        List<Task> tasks = taskRepository.findByWorkflowInstance(instance);
        
        // Находим текущий шаг
        Integer currentStep = tasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.PENDING)
                .map(Task::getStepOrder)
                .min(Integer::compareTo)
                .orElse(null);

        if (currentStep == null) {
            // Все tasks завершены
            instance.setStatus(WorkFlowStatus.COMPLETED);
            instance.setCompletedAt(LocalDateTime.now());
            log.info("Workflow completed: {}", instance.getId());
            
            // Логируем завершение
            auditService.logWorkflowCompleted(instance);
            
            // Отправляем email уведомление
            emailNotificationService.notifyWorkflowCompleted(instance);
            
            // Отправляем уведомление о завершении
            Long companyId = instance.getDocument().getCompany().getId();
            eventBroadcaster.broadcastWorkflowCompleted(companyId, instance.getId());
            return;
        }

        // Проверяем, все ли tasks текущего шага завершены
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
     * Проверяет, может ли пользователь одобрить task
     */
    public boolean canUserApproveTask(Task task, User user) {
        // Получаем роль пользователя в компании
        var company = task.getWorkflowInstance().getDocument().getCompany();
        
        var userRole = user.getMemberships().stream()
                .filter(m -> m.getCompany().getId().equals(company.getId()))
                .map(m -> m.getRole().getLevel())
                .findFirst()
                .orElse(0);

        boolean canApprove = userRole >= task.getRequiredRoleLevel();
        log.debug("User {} role level {} can approve task requiring level {}: {}", 
            user.getEmail(), userRole, task.getRequiredRoleLevel(), canApprove);
        
        return canApprove;
    }
}
