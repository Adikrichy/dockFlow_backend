package org.aldousdev.dockflowbackend.workflow.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.auth.repository.MembershipRepository;
import org.aldousdev.dockflowbackend.workflow.entity.Document;
import org.aldousdev.dockflowbackend.workflow.entity.RoutingRule;
import org.aldousdev.dockflowbackend.workflow.entity.Task;
import org.aldousdev.dockflowbackend.workflow.entity.WorkflowInstance;
import org.aldousdev.dockflowbackend.workflow.enums.RoutingType;
import org.aldousdev.dockflowbackend.workflow.enums.TaskStatus;
import org.aldousdev.dockflowbackend.workflow.enums.WorkFlowStatus;
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

            // Группируем шаги по порядку
            Map<Integer, List<WorkflowXmlParser.WorkflowStep>> groupedSteps =
                steps.stream().collect(Collectors.groupingBy(WorkflowXmlParser.WorkflowStep::getOrder));

            for (Map.Entry<Integer, List<WorkflowXmlParser.WorkflowStep>> entry : groupedSteps.entrySet()) {
                Integer stepOrder = entry.getKey();
                List<WorkflowXmlParser.WorkflowStep> stepGroup = entry.getValue();

                // Проверяем, есть ли параллельные шаги
                boolean hasParallelSteps = stepGroup.stream().anyMatch(WorkflowXmlParser.WorkflowStep::isParallel);

                if (hasParallelSteps) {
                    // Создаем параллельные задачи для всех пользователей с соответствующими ролями
                    createParallelTasks(workflowInstance, stepOrder, stepGroup);
                } else {
                    // Обычное последовательное выполнение
                    for (WorkflowXmlParser.WorkflowStep step : stepGroup) {
                        createTask(workflowInstance, stepOrder, step);
                    }
                }
            }

            workflowInstance.setStatus(WorkFlowStatus.IN_PROGRESS);
            log.info("Workflow initialized with {} steps", groupedSteps.size());

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
     * Создает параллельные задачи для всех пользователей с соответствующими ролями
     */
    private void createParallelTasks(WorkflowInstance instance, Integer stepOrder,
                                   List<WorkflowXmlParser.WorkflowStep> parallelSteps) {
        log.info("Creating parallel tasks for step {} with {} parallel roles",
                stepOrder, parallelSteps.size());

        // Группируем шаги по роли (на случай дублирования ролей)
        Map<String, WorkflowXmlParser.WorkflowStep> stepsByRole = parallelSteps.stream()
            .collect(Collectors.toMap(
                WorkflowXmlParser.WorkflowStep::getRoleName,
                step -> step,
                (existing, replacement) -> existing // берем первый, если дублирование
            ));

        // Для каждой роли создаем задачу для всех пользователей с этой ролью
        for (Map.Entry<String, WorkflowXmlParser.WorkflowStep> entry : stepsByRole.entrySet()) {
            String roleName = entry.getKey();
            WorkflowXmlParser.WorkflowStep step = entry.getValue();

            log.debug("Creating parallel tasks for role {} at step {}", roleName, stepOrder);

            // Получаем всех пользователей компании с требуемой ролью
            List<User> usersWithRole = getUsersWithRoleInCompany(
                roleName,
                step.getRoleLevel(),
                instance.getDocument().getCompany().getId()
            );

            if (usersWithRole.isEmpty()) {
                log.warn("No users found with role {} and level >= {} in company {}",
                        roleName, step.getRoleLevel(), instance.getDocument().getCompany().getId());
                // Создаем задачу без конкретного назначения
                createTask(instance, stepOrder, step);
            } else {
                // Создаем задачу для каждого пользователя с этой ролью
                for (User user : usersWithRole) {
                    createTaskForSpecificUser(instance, stepOrder, step, user);
                }
            }
        }
    }

    /**
     * Создает task для конкретного пользователя
     */
    private void createTaskForSpecificUser(WorkflowInstance instance, Integer stepOrder,
                                         WorkflowXmlParser.WorkflowStep step, User assignedUser) {
        log.debug("Creating task for user {} at step {}", assignedUser.getEmail(), stepOrder);

        Task task = Task.builder()
                .workflowInstance(instance)
                .stepOrder(stepOrder)
                .requiredRoleName(step.getRoleName())
                .requiredRoleLevel(step.getRoleLevel())
                .status(TaskStatus.PENDING)
                .assignedBy(instance.getInitiatedBy())
                .assignedTo(assignedUser) // Новое поле для конкретного назначения
                .build();

        taskRepository.save(task);

        // Логируем создание task
        auditService.logTaskCreated(task, step.getRoleName());

        // Отправляем уведомление конкретному пользователю
        Long companyId = instance.getDocument().getCompany().getId();
        eventBroadcaster.broadcastTaskCreated(companyId, task.getId(), step.getRoleName());
        eventBroadcaster.broadcastTaskAssigned(companyId, task.getId(), assignedUser.getId());
    }

    /**
     * Получает всех пользователей компании с указанной ролью и уровнем
     */
    private List<User> getUsersWithRoleInCompany(String roleName, Integer minRoleLevel, Long companyId) {
        log.debug("Finding users with role {} and level >= {} in company {}",
                roleName, minRoleLevel, companyId);

        return membershipRepository.findUsersByCompanyIdAndRoleNameAndMinLevel(
            companyId, roleName, minRoleLevel);
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

        // Определяем следующий шаг на основе условий
        determineAndMoveToNextStep(task.getWorkflowInstance(), task, true);
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
                // Используем новый conditional routing для возврата на шаг
                determineAndMoveToNextStep(instance, task, false);
            }
        } else {
            // Используем conditional routing для reject
            determineAndMoveToNextStep(instance, task, false);
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
     * Определяет следующий шаг workflow на основе условий
     */
    public Integer determineNextStep(WorkflowInstance instance, Task completedTask,
                                   boolean wasApproved, String workflowXml) {
        log.info("Determining next step for workflow {} after task {} ({})",
                instance.getId(), completedTask.getId(), wasApproved ? "approved" : "rejected");

        try {
            Document document = instance.getDocument();

            // Ищем подходящее правило маршрутизации
            RoutingType routingType = wasApproved ? RoutingType.ON_APPROVE : RoutingType.ON_REJECT;

            RoutingRule applicableRule = routingRuleRepository
                .findByTemplateAndStepOrderAndRoutingType(
                    instance.getTemplate(),
                    completedTask.getStepOrder(),
                    routingType
                )
                .filter(rule -> {
                    // Проверяем условие, если оно есть
                    String condition = rule.getCondition();
                    return condition == null || ConditionEvaluator.evaluate(condition, document);
                })
                .orElse(null);

            if (applicableRule != null) {
                Integer targetStep = applicableRule.getTargetStep();
                log.info("Found routing rule: step {} -> targetStep {} (condition: {})",
                        completedTask.getStepOrder(), targetStep, applicableRule.getCondition());

                if (targetStep == null) {
                    // Завершить workflow
                    log.info("Routing rule indicates workflow completion");
                    return null;
                }

                return targetStep;
            }

            // Если нет подходящего правила, используем стандартную логику
            return getNextSequentialStep(instance, completedTask.getStepOrder(), workflowXml);

        } catch (Exception e) {
            log.error("Error determining next step: {}", e.getMessage(), e);
            // В случае ошибки возвращаем следующий sequential шаг
            return getNextSequentialStep(instance, completedTask.getStepOrder(), workflowXml);
        }
    }

    /**
     * Получает следующий sequential шаг (для fallback логики)
     */
    private Integer getNextSequentialStep(WorkflowInstance instance, Integer currentStep, String workflowXml) {
        try {
            WorkflowXmlParser.WorkflowDefinition definition =
                WorkflowXmlParser.parseWorkflowDefinition(workflowXml);

            // Находим максимальный order среди шагов
            int maxOrder = definition.getSteps().stream()
                .mapToInt(WorkflowXmlParser.WorkflowStep::getOrder)
                .max()
                .orElse(currentStep);

            // Если текущий шаг не последний, возвращаем следующий
            if (currentStep < maxOrder) {
                return currentStep + 1;
            }

            // Это последний шаг - завершить workflow
            return null;

        } catch (Exception e) {
            log.error("Error getting next sequential step: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Определяет следующий шаг и перемещает workflow
     */
    @Transactional
    public void determineAndMoveToNextStep(WorkflowInstance instance, Task completedTask, boolean wasApproved) {
        log.info("Determining and moving to next step after task {} ({})",
                completedTask.getId(), wasApproved ? "approved" : "rejected");

        try {
            String workflowXml = instance.getTemplate().getWorkflowXml();
            Integer nextStep = determineNextStep(instance, completedTask, wasApproved, workflowXml);

            if (nextStep == null) {
                // Workflow завершен
                completeWorkflow(instance, wasApproved);
            } else {
                // Создаем задачи для следующего шага
                createTasksForStep(instance, nextStep, workflowXml);

                // Отправляем WebSocket уведомления участникам следующего шага
                notifyNextStepParticipants(instance, nextStep, workflowXml);
            }

        } catch (Exception e) {
            log.error("Error in determineAndMoveToNextStep: {}", e.getMessage(), e);
            // Fallback: завершаем workflow
            completeWorkflow(instance, false);
        }
    }

    /**
     * Завершает workflow
     */
    private void completeWorkflow(WorkflowInstance instance, boolean successful) {
        instance.setStatus(successful ? WorkFlowStatus.COMPLETED : WorkFlowStatus.REJECTED);
        instance.setCompletedAt(LocalDateTime.now());

        log.info("Workflow {} completed ({})", instance.getId(),
                successful ? "successfully" : "with rejection");

        // Логируем завершение
        if (successful) {
            auditService.logWorkflowCompleted(instance);
            emailNotificationService.notifyWorkflowCompleted(instance);
        } else {
            auditService.logWorkflowRejected(instance, "Completed via conditional routing");
            emailNotificationService.notifyWorkflowRejected(instance, "Completed via conditional routing");
        }

        // Отправляем WebSocket уведомление
        Long companyId = instance.getDocument().getCompany().getId();
        if (successful) {
            eventBroadcaster.broadcastWorkflowCompleted(companyId, instance.getId());
        } else {
            eventBroadcaster.broadcastWorkflowRejected(companyId, instance.getId(),
                "Completed via conditional routing");
        }
    }

    /**
     * Уведомляет участников следующего шага
     */
    private void notifyNextStepParticipants(WorkflowInstance instance, Integer nextStep, String workflowXml) {
        try {
            WorkflowXmlParser.WorkflowDefinition definition =
                WorkflowXmlParser.parseWorkflowDefinition(workflowXml);

            // Находим роли для следующего шага
            List<String> rolesForNextStep = definition.getSteps().stream()
                .filter(step -> step.getOrder().equals(nextStep))
                .map(WorkflowXmlParser.WorkflowStep::getRoleName)
                .distinct()
                .toList();

            // Отправляем уведомления всем пользователям с этими ролями
            Long companyId = instance.getDocument().getCompany().getId();
            for (String roleName : rolesForNextStep) {
                eventBroadcaster.broadcastTaskCreated(companyId, null, roleName); // null taskId для общего уведомления
            }

        } catch (Exception e) {
            log.error("Error notifying next step participants: {}", e.getMessage());
        }
    }

    /**
     * Создает задачи для указанного шага
     */
    public void createTasksForStep(WorkflowInstance instance, Integer stepOrder, String workflowXml) {
        log.info("Creating tasks for step {} in workflow {}", stepOrder, instance.getId());

        try {
            WorkflowXmlParser.WorkflowDefinition definition =
                WorkflowXmlParser.parseWorkflowDefinition(workflowXml);

            // Находим шаги с указанным order
            List<WorkflowXmlParser.WorkflowStep> stepsForOrder = definition.getSteps().stream()
                .filter(step -> step.getOrder().equals(stepOrder))
                .toList();

            if (stepsForOrder.isEmpty()) {
                log.warn("No steps found for order {} in workflow {}", stepOrder, instance.getId());
                return;
            }

            // Проверяем, есть ли параллельные шаги
            boolean hasParallelSteps = stepsForOrder.stream().anyMatch(WorkflowXmlParser.WorkflowStep::isParallel);

            if (hasParallelSteps) {
                // Создаем параллельные задачи
                createParallelTasks(instance, stepOrder, stepsForOrder);
            } else {
                // Создаем обычные задачи (для совместимости)
                for (WorkflowXmlParser.WorkflowStep step : stepsForOrder) {
                    createTask(instance, stepOrder, step);
                }
            }

        } catch (Exception e) {
            log.error("Error creating tasks for step {}: {}", stepOrder, e.getMessage(), e);
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
