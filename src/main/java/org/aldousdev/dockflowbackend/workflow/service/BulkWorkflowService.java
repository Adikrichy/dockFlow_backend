package org.aldousdev.dockflowbackend.workflow.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.workflow.dto.request.BulkTaskRequest;
import org.aldousdev.dockflowbackend.workflow.dto.response.BulkOperationResponse;
import org.aldousdev.dockflowbackend.workflow.engine.WorkflowEngine;
import org.aldousdev.dockflowbackend.workflow.entity.Task;
import org.aldousdev.dockflowbackend.workflow.enums.TaskStatus;
import org.aldousdev.dockflowbackend.workflow.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BulkWorkflowService {

    private final TaskRepository taskRepository;
    private final WorkflowEngine workflowEngine;

    /**
     * Одобряет несколько задач одновременно
     */
    @Transactional
    public BulkOperationResponse bulkApprove(List<Task> tasks, User user, String comment) {
        log.info("Bulk approving {} tasks by user {}", tasks.size(), user.getEmail());

        List<Long> successfulIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (Task task : tasks) {
            try {
                // Проверяем права доступа
                if (!workflowEngine.canUserApproveTask(task, user)) {
                    errors.add("Task " + task.getId() + ": insufficient permissions");
                    continue;
                }

                // Проверяем статус
                if (task.getStatus() != TaskStatus.PENDING) {
                    errors.add("Task " + task.getId() + ": not in PENDING status");
                    continue;
                }

                // Одобряем задачу
                workflowEngine.approveTask(task, user, comment);
                successfulIds.add(task.getId());

            } catch (Exception e) {
                log.error("Error approving task {}", task.getId(), e);
                errors.add("Task " + task.getId() + ": " + e.getMessage());
            }
        }

        BulkOperationResponse response = new BulkOperationResponse();
        response.setSuccessfulTaskIds(successfulIds);
        response.setErrors(errors);
        response.setTotalTasks(tasks.size());
        response.setSuccessfulCount(successfulIds.size());

        log.info("Bulk approval completed: {}/{} successful", successfulIds.size(), tasks.size());
        return response;
    }

    /**
     * Отклоняет несколько задач одновременно
     */
    @Transactional
    public BulkOperationResponse bulkReject(List<Task> tasks, User user, String comment) {
        log.info("Bulk rejecting {} tasks by user {}", tasks.size(), user.getEmail());

        List<Long> successfulIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (Task task : tasks) {
            try {
                // Проверяем права доступа
                if (!workflowEngine.canUserApproveTask(task, user)) {
                    errors.add("Task " + task.getId() + ": insufficient permissions");
                    continue;
                }

                // Проверяем статус
                if (task.getStatus() != TaskStatus.PENDING) {
                    errors.add("Task " + task.getId() + ": not in PENDING status");
                    continue;
                }

                // Отклоняем задачу
                workflowEngine.rejectTask(task, user, comment);
                successfulIds.add(task.getId());

            } catch (Exception e) {
                log.error("Error rejecting task {}", task.getId(), e);
                errors.add("Task " + task.getId() + ": " + e.getMessage());
            }
        }

        BulkOperationResponse response = new BulkOperationResponse();
        response.setSuccessfulTaskIds(successfulIds);
        response.setErrors(errors);
        response.setTotalTasks(tasks.size());
        response.setSuccessfulCount(successfulIds.size());

        log.info("Bulk rejection completed: {}/{} successful", successfulIds.size(), tasks.size());
        return response;
    }

    /**
     * Получает задачи с проверкой прав доступа пользователя
     */
    public List<Task> getTasksWithAccessCheck(List<Long> taskIds, User user, Long companyId) {
        List<Task> tasks = taskRepository.findAllById(taskIds);

        // Фильтруем по компании и правам доступа
        return tasks.stream()
            .filter(task -> {
                // Проверяем, что задача принадлежит той же компании
                Long taskCompanyId = task.getWorkflowInstance().getDocument().getCompany().getId();
                if (!taskCompanyId.equals(companyId)) {
                    return false;
                }

                // Проверяем, что пользователь может одобрить эту задачу
                return workflowEngine.canUserApproveTask(task, user);
            })
            .collect(Collectors.toList());
    }
}
