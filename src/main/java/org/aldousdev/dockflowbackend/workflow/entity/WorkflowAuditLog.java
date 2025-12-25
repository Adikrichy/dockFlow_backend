package org.aldousdev.dockflowbackend.workflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.aldousdev.dockflowbackend.auth.entity.User;

import java.time.LocalDateTime;

@Entity
@Table(name = "workflow_audit_logs", indexes = {
        @Index(name = "idx_workflow_instance", columnList = "workflow_instance_id"),
        @Index(name = "idx_task", columnList = "task_id"),
        @Index(name = "idx_timestamp", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowAuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_instance_id", nullable = false)
    private WorkflowInstance workflowInstance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User performedBy;

    /**
     * Тип действия:
     * WORKFLOW_STARTED, TASK_CREATED, TASK_APPROVED, TASK_REJECTED,
     * TASK_REASSIGNED, WORKFLOW_COMPLETED, WORKFLOW_REJECTED, etc.
     */
    @Column(name = "action_type", nullable = false)
    private String actionType;

    /**
     * Описание действия
     */
    @Column(name = "description", length = 1000)
    private String description;

    /**
     * Дополнительные данные в JSON (для сохранения контекста)
     * Например: { "roleLevel": 80, "approvalTime": "2024-01-15T12:30:00" }
     */
    @Column(columnDefinition = "TEXT")
    private String metadata;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * IP адрес пользователя (если применимо)
     */
    @Column(length = 50)
    private String ipAddress;
}
