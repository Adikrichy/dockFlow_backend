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
     * Sends a simple text email
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
     * Sends an HTML email
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
     * Sends an email about a new task creation
     */
    public void sendTaskCreatedEmail(String userEmail, String userName, String roleName, 
                                     String documentName, Long taskId, Long workflowId) {
        String subject = "New task for approval: " + documentName;
        String body = buildTaskCreatedEmailBody(userName, roleName, documentName, taskId, workflowId);
        sendHtmlEmail(userEmail, subject, body);
    }

    /**
     * Sends an email about task approval
     */
    public void sendTaskApprovedEmail(String userEmail, String approvedBy, String documentName, 
                                     Integer stepOrder, Integer totalSteps) {
        String subject = "Task approved: " + documentName;
        String body = buildTaskApprovedEmailBody(approvedBy, documentName, stepOrder, totalSteps);
        sendHtmlEmail(userEmail, subject, body);
    }

    /**
     * Sends an email about task rejection
     */
    public void sendTaskRejectedEmail(String userEmail, String rejectedBy, String documentName, 
                                     String reason, Integer returnToStep) {
        String subject = "Task rejected: " + documentName;
        String body = buildTaskRejectedEmailBody(rejectedBy, documentName, reason, returnToStep);
        sendHtmlEmail(userEmail, subject, body);
    }

    /**
     * Sends an email about workflow completion
     */
    public void sendWorkflowCompletedEmail(String userEmail, String documentName, String initiatedBy) {
        String subject = "Document approved: " + documentName;
        String body = buildWorkflowCompletedEmailBody(documentName, initiatedBy);
        sendHtmlEmail(userEmail, subject, body);
    }

    /**
     * Sends an email about workflow rejection
     */
    public void sendWorkflowRejectedEmail(String userEmail, String documentName, String reason) {
        String subject = "Document rejected: " + documentName;
        String body = buildWorkflowRejectedEmailBody(documentName, reason);
        sendHtmlEmail(userEmail, subject, body);
    }

    /**
     * Builds HTML for task creation email
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
                            <h1>New Task for Approval</h1>
                        </div>
                        <div class="content">
                            <p>Hello, %s!</p>
                            <p>You have a new task for document approval:</p>
                            <h2>%s</h2>
                            <p><strong>Role:</strong> %s</p>
                            <p><strong>Action Type:</strong> Approval required</p>
                            <a href="%s/workflow/%d/task/%d" class="button">Go to Task</a>
                        </div>
                        <div class="footer">
                            <p>© 2024 DocFlow. All rights reserved.</p>
                        </div>
                    </div>
                </body>
                </html>
                """, userName, documentName, roleName, appUrl, workflowId, taskId);
    }

    /**
     * Builds HTML for task approval email
     */
    private String buildTaskApprovedEmailBody(String approvedBy, String documentName, 
                                             Integer stepOrder, Integer totalSteps) {
        String progress = stepOrder + " of " + totalSteps;
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
                            <h1>Task Approved ✓</h1>
                        </div>
                        <div class="content">
                            <p>Document <strong>%s</strong> has been approved by user <strong>%s</strong>.</p>
                            <p><strong>Approval Progress:</strong></p>
                            <div class="progress">
                                <div class="progress-bar"></div>
                            </div>
                            <p>%s steps completed</p>
                        </div>
                        <div class="footer">
                            <p>© 2024 DocFlow. All rights reserved.</p>
                        </div>
                    </div>
                </body>
                </html>
                """, (stepOrder * 100 / totalSteps), documentName, approvedBy, progress);
    }

    /**
     * Builds HTML for task rejection email
     */
    private String buildTaskRejectedEmailBody(String rejectedBy, String documentName, 
                                             String reason, Integer returnToStep) {
        String returnInfo = returnToStep != null 
            ? "The document has been returned for review to step " + returnToStep
            : "The document has been rejected completely";
            
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
                            <h1>Task Rejected ✗</h1>
                        </div>
                        <div class="content">
                            <p>Document <strong>%s</strong> has been rejected by user <strong>%s</strong>.</p>
                            <p><strong>%s</strong></p>
                            <div class="reason">
                                <p><strong>Comment:</strong></p>
                                <p>%s</p>
                            </div>
                        </div>
                        <div class="footer">
                            <p>© 2024 DocFlow. All rights reserved.</p>
                        </div>
                    </div>
                </body>
                </html>
                """, documentName, rejectedBy, returnInfo, reason);
    }

    /**
     * Builds HTML for workflow completion email
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
                            <h1>Document Successfully Approved</h1>
                        </div>
                        <div class="content">
                            <div class="success">✓</div>
                            <p>All approvals for document <strong>%s</strong> (initiated by %s) are complete.</p>
                            <p>The document is ready for use.</p>
                        </div>
                        <div class="footer">
                            <p>© 2024 DocFlow. All rights reserved.</p>
                        </div>
                    </div>
                </body>
                </html>
                """, documentName, initiatedBy);
    }

    /**
     * Builds HTML for workflow rejection email
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
                            <h1>Document Rejected</h1>
                        </div>
                        <div class="content">
                            <div class="rejected">✗</div>
                            <p>Approval for document <strong>%s</strong> was rejected.</p>
                            <p><strong>Reason:</strong></p>
                            <p>%s</p>
                        </div>
                        <div class="footer">
                            <p>© 2024 DocFlow. All rights reserved.</p>
                        </div>
                    </div>
                </body>
                </html>
                """, documentName, reason);
    }
}
