package org.aldousdev.dockflowbackend.workflow.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.workflow.entity.Task;
import org.aldousdev.dockflowbackend.workflow.entity.WorkflowInstance;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationService {
    private final EmailService emailService;

    /**
     * Отправляет уведомление о новой task всем пользователям с нужной ролью
     */
    @Async
    public void notifyTaskCreated(Task task, String roleName) {
        log.info("Sending task created notification for role: {}", roleName);
        
        try {
            WorkflowInstance instance = task.getWorkflowInstance();
            String documentName = instance.getDocument().getOriginalFilename();
            
            // TODO: Получить список пользователей с данной ролью и компанией
            // List<User> users = getUsersByRoleAndCompany(roleName, company);
            
            // for (User user : users) {
            //     emailService.sendTaskCreatedEmail(
            //         user.getEmail(), 
            //         user.getFirstName(), 
            //         roleName,
            //         documentName,
            //         task.getId(),
            //         instance.getId()
            //     );
            // }
            
            log.info("Task creation notifications sent for task: {}", task.getId());
        } catch (Exception e) {
            log.error("Error sending task created notification", e);
        }
    }

    /**
     * Отправляет уведомление об одобрении task
     */
    @Async
    public void notifyTaskApproved(Task task, User approvedBy) {
        log.info("Sending task approved notification");
        
        try {
            WorkflowInstance instance = task.getWorkflowInstance();
            String documentName = instance.getDocument().getOriginalFilename();
            String initiatorEmail = instance.getInitiatedBy().getEmail();
            
            // Подсчитываем все tasks и сколько одобрено
            List<Task> tasks = (instance.getTasks() != null ? instance.getTasks() : List.of());
            long totalSteps = tasks.stream()
                    .map(Task::getStepOrder)
                    .distinct()
                    .count();
            
            long approvedSteps = tasks.stream()
                    .filter(t -> t.getStepOrder() <= task.getStepOrder())
                    .map(Task::getStepOrder)
                    .distinct()
                    .count();
            
            emailService.sendTaskApprovedEmail(
                initiatorEmail,
                approvedBy.getEmail(),
                documentName,
                (int) approvedSteps,
                (int) totalSteps
            );
            
            log.info("Task approved notification sent for task: {}", task.getId());
        } catch (Exception e) {
            log.error("Error sending task approved notification", e);
        }
    }

    /**
     * Отправляет уведомление об отклонении task
     */
    @Async
    public void notifyTaskRejected(Task task, User rejectedBy, String comment) {
        log.info("Sending task rejected notification");
        
        try {
            WorkflowInstance instance = task.getWorkflowInstance();
            String documentName = instance.getDocument().getOriginalFilename();
            String initiatorEmail = instance.getInitiatedBy().getEmail();
            
            // Получаем информацию о шаге возврата (если применимо правило маршрутизации)
            Integer returnToStep = task.getStepOrder(); // Может быть переопределено
            
            emailService.sendTaskRejectedEmail(
                initiatorEmail,
                rejectedBy.getEmail(),
                documentName,
                comment,
                returnToStep
            );
            
            log.info("Task rejected notification sent for task: {}", task.getId());
        } catch (Exception e) {
            log.error("Error sending task rejected notification", e);
        }
    }

    /**
     * Отправляет уведомление о завершении workflow
     */
    @Async
    public void notifyWorkflowCompleted(WorkflowInstance instance) {
        log.info("Sending workflow completed notification");
        
        try {
            String documentName = instance.getDocument().getOriginalFilename();
            String initiatedBy = instance.getInitiatedBy().getFirstName() + " " + 
                               instance.getInitiatedBy().getLastName();
            
            // Уведомляем инициатора и всех участников
            String initiatorEmail = instance.getInitiatedBy().getEmail();
            
            emailService.sendWorkflowCompletedEmail(
                initiatorEmail,
                documentName,
                initiatedBy
            );
            
            // TODO: Отправить уведомления другим участникам
            
            log.info("Workflow completed notification sent for instance: {}", instance.getId());
        } catch (Exception e) {
            log.error("Error sending workflow completed notification", e);
        }
    }

    /**
     * Отправляет уведомление об отклонении workflow
     */
    @Async
    public void notifyWorkflowRejected(WorkflowInstance instance, String reason) {
        log.info("Sending workflow rejected notification");
        
        try {
            String documentName = instance.getDocument().getOriginalFilename();
            String initiatorEmail = instance.getInitiatedBy().getEmail();
            
            emailService.sendWorkflowRejectedEmail(
                initiatorEmail,
                documentName,
                reason
            );
            
            log.info("Workflow rejected notification sent for instance: {}", instance.getId());
        } catch (Exception e) {
            log.error("Error sending workflow rejected notification", e);
        }
    }

    /**
     * Отправляет напоминание о pending task
     */
    @Async
    public void sendTaskReminderEmail(Task task, User user) {
        log.info("Sending task reminder email to: {}", user.getEmail());
        
        try {
            WorkflowInstance instance = task.getWorkflowInstance();
            String documentName = instance.getDocument().getOriginalFilename();
            String roleName = task.getRequiredRoleName();
            
            String subject = "Напоминание: ожидается ваше одобрение - " + documentName;
            String body = buildTaskReminderBody(user.getFirstName(), documentName, roleName);
            
            emailService.sendHtmlEmail(user.getEmail(), subject, body);
            
            log.info("Task reminder sent for task: {}", task.getId());
        } catch (Exception e) {
            log.error("Error sending task reminder email", e);
        }
    }

    private String buildTaskReminderBody(String userName, String documentName, String roleName) {
        return String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: Arial, sans-serif; }
                        .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                        .header { background-color: #FF9800; color: white; padding: 20px; border-radius: 5px; }
                        .content { padding: 20px; background-color: #f5f5f5; }
                        .button { background-color: #FF9800; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px; display: inline-block; margin-top: 10px; }
                        .footer { padding: 10px; text-align: center; color: #666; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>Напоминание: ожидается ваше одобрение</h1>
                        </div>
                        <div class="content">
                            <p>Привет, %s!</p>
                            <p>Это напоминание о том, что вы еще не одобрили документ:</p>
                            <h2>%s</h2>
                            <p><strong>Требуемая роль:</strong> %s</p>
                            <p>Пожалуйста, обновите статус документа при вашей ближайшей возможности.</p>
                            <a href="http://localhost:3000/tasks" class="button">Перейти к задачам</a>
                        </div>
                        <div class="footer">
                            <p>© 2024 DocFlow. Все права защищены.</p>
                        </div>
                    </div>
                </body>
                </html>
                """, userName, documentName, roleName);
    }
}
