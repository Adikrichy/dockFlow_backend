package org.aldousdev.dockflowbackend.workflow.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    private final JavaMailSender mailSender;

    @Value("${spring.mail.from:noreply@dockflow.com}")
    private String fromEmail;

    @Value("${app.url:http://localhost:3000}")
    private String appUrl;

    /**
     * Отправляет простое текстовое письмо
     */
    public void sendSimpleEmail(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            
            mailSender.send(message);
            log.info("Email sent to {} with subject: {}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
        }
    }

    /**
     * Отправляет HTML письмо
     */
    public void sendHtmlEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            
            mailSender.send(message);
            log.info("HTML email sent to {} with subject: {}", to, subject);
        } catch (MessagingException e) {
            log.error("Failed to send HTML email to {}: {}", to, e.getMessage(), e);
        }
    }

    /**
     * Отправляет письмо о создании новой task
     */
    public void sendTaskCreatedEmail(String userEmail, String userName, String roleName, 
                                     String documentName, Long taskId, Long workflowId) {
        String subject = "Новая задача на одобрение: " + documentName;
        String body = buildTaskCreatedEmailBody(userName, roleName, documentName, taskId, workflowId);
        sendHtmlEmail(userEmail, subject, body);
    }

    /**
     * Отправляет письмо об одобрении task
     */
    public void sendTaskApprovedEmail(String userEmail, String approvedBy, String documentName, 
                                     Integer stepOrder, Integer totalSteps) {
        String subject = "Задача одобрена: " + documentName;
        String body = buildTaskApprovedEmailBody(approvedBy, documentName, stepOrder, totalSteps);
        sendHtmlEmail(userEmail, subject, body);
    }

    /**
     * Отправляет письмо об отклонении task
     */
    public void sendTaskRejectedEmail(String userEmail, String rejectedBy, String documentName, 
                                     String reason, Integer returnToStep) {
        String subject = "Задача отклонена: " + documentName;
        String body = buildTaskRejectedEmailBody(rejectedBy, documentName, reason, returnToStep);
        sendHtmlEmail(userEmail, subject, body);
    }

    /**
     * Отправляет письмо о завершении workflow
     */
    public void sendWorkflowCompletedEmail(String userEmail, String documentName, String initiatedBy) {
        String subject = "Документ одобрен: " + documentName;
        String body = buildWorkflowCompletedEmailBody(documentName, initiatedBy);
        sendHtmlEmail(userEmail, subject, body);
    }

    /**
     * Отправляет письмо об отклонении workflow
     */
    public void sendWorkflowRejectedEmail(String userEmail, String documentName, String reason) {
        String subject = "Документ отклонен: " + documentName;
        String body = buildWorkflowRejectedEmailBody(documentName, reason);
        sendHtmlEmail(userEmail, subject, body);
    }

    /**
     * Строит HTML для письма о создании task
     */
    private String buildTaskCreatedEmailBody(String userName, String roleName, String documentName, 
                                            Long taskId, Long workflowId) {
        return String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: Arial, sans-serif; }
                        .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                        .header { background-color: #2196F3; color: white; padding: 20px; border-radius: 5px; }
                        .content { padding: 20px; background-color: #f5f5f5; }
                        .button { background-color: #2196F3; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px; display: inline-block; margin-top: 10px; }
                        .footer { padding: 10px; text-align: center; color: #666; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>Новая задача на одобрение</h1>
                        </div>
                        <div class="content">
                            <p>Привет, %s!</p>
                            <p>У вас есть новая задача на одобрение документа:</p>
                            <h2>%s</h2>
                            <p><strong>Роль:</strong> %s</p>
                            <p><strong>Тип действия:</strong> Требуется ваше одобрение</p>
                            <a href="%s/workflow/%d/task/%d" class="button">Перейти к задаче</a>
                        </div>
                        <div class="footer">
                            <p>© 2024 DocFlow. Все права защищены.</p>
                        </div>
                    </div>
                </body>
                </html>
                """, userName, documentName, roleName, appUrl, workflowId, taskId);
    }

    /**
     * Строит HTML для письма об одобрении task
     */
    private String buildTaskApprovedEmailBody(String approvedBy, String documentName, 
                                             Integer stepOrder, Integer totalSteps) {
        String progress = stepOrder + " из " + totalSteps;
        return String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: Arial, sans-serif; }
                        .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                        .header { background-color: #4CAF50; color: white; padding: 20px; border-radius: 5px; }
                        .content { padding: 20px; background-color: #f5f5f5; }
                        .progress { background-color: #ddd; border-radius: 5px; height: 20px; margin: 10px 0; }
                        .progress-bar { background-color: #4CAF50; height: 100%%; border-radius: 5px; width: %d%%; }
                        .footer { padding: 10px; text-align: center; color: #666; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>Задача одобрена ✓</h1>
                        </div>
                        <div class="content">
                            <p>Документ <strong>%s</strong> был одобрен пользователем <strong>%s</strong>.</p>
                            <p><strong>Прогресс согласования:</strong></p>
                            <div class="progress">
                                <div class="progress-bar"></div>
                            </div>
                            <p>%s шагов завершено</p>
                        </div>
                        <div class="footer">
                            <p>© 2024 DocFlow. Все права защищены.</p>
                        </div>
                    </div>
                </body>
                </html>
                """, (stepOrder * 100 / totalSteps), documentName, approvedBy, progress);
    }

    /**
     * Строит HTML для письма об отклонении task
     */
    private String buildTaskRejectedEmailBody(String rejectedBy, String documentName, 
                                             String reason, Integer returnToStep) {
        String returnInfo = returnToStep != null 
            ? "Документ был отправлен на пересмотр на шаг " + returnToStep
            : "Документ был отклонен полностью";
            
        return String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: Arial, sans-serif; }
                        .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                        .header { background-color: #f44336; color: white; padding: 20px; border-radius: 5px; }
                        .content { padding: 20px; background-color: #f5f5f5; }
                        .reason { background-color: #ffebee; padding: 10px; border-left: 4px solid #f44336; }
                        .footer { padding: 10px; text-align: center; color: #666; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>Задача отклонена ✗</h1>
                        </div>
                        <div class="content">
                            <p>Документ <strong>%s</strong> был отклонен пользователем <strong>%s</strong>.</p>
                            <p><strong>%s</strong></p>
                            <div class="reason">
                                <p><strong>Комментарий:</strong></p>
                                <p>%s</p>
                            </div>
                        </div>
                        <div class="footer">
                            <p>© 2024 DocFlow. Все права защищены.</p>
                        </div>
                    </div>
                </body>
                </html>
                """, documentName, rejectedBy, returnInfo, reason);
    }

    /**
     * Строит HTML для письма о завершении workflow
     */
    private String buildWorkflowCompletedEmailBody(String documentName, String initiatedBy) {
        return String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: Arial, sans-serif; }
                        .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                        .header { background-color: #4CAF50; color: white; padding: 20px; border-radius: 5px; }
                        .content { padding: 20px; background-color: #f5f5f5; }
                        .success { font-size: 60px; text-align: center; color: #4CAF50; }
                        .footer { padding: 10px; text-align: center; color: #666; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>Документ успешно одобрен</h1>
                        </div>
                        <div class="content">
                            <div class="success">✓</div>
                            <p>Все согласования для документа <strong>%s</strong> (инициирован %s) завершены.</p>
                            <p>Документ готов к использованию.</p>
                        </div>
                        <div class="footer">
                            <p>© 2024 DocFlow. Все права защищены.</p>
                        </div>
                    </div>
                </body>
                </html>
                """, documentName, initiatedBy);
    }

    /**
     * Строит HTML для письма об отклонении workflow
     */
    private String buildWorkflowRejectedEmailBody(String documentName, String reason) {
        return String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: Arial, sans-serif; }
                        .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                        .header { background-color: #f44336; color: white; padding: 20px; border-radius: 5px; }
                        .content { padding: 20px; background-color: #f5f5f5; }
                        .rejected { font-size: 60px; text-align: center; color: #f44336; }
                        .footer { padding: 10px; text-align: center; color: #666; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>Документ отклонен</h1>
                        </div>
                        <div class="content">
                            <div class="rejected">✗</div>
                            <p>Согласование документа <strong>%s</strong> было отклонено.</p>
                            <p><strong>Причина:</strong></p>
                            <p>%s</p>
                        </div>
                        <div class="footer">
                            <p>© 2024 DocFlow. Все права защищены.</p>
                        </div>
                    </div>
                </body>
                </html>
                """, documentName, reason);
    }
}
