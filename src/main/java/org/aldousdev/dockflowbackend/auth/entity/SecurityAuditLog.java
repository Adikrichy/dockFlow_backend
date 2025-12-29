package org.aldousdev.dockflowbackend.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity для логирования событий безопасности
 */
@Entity
@Table(name = "security_audit_logs", indexes = {
    @Index(name = "idx_security_audit_user", columnList = "user_id"),
    @Index(name = "idx_security_audit_event", columnList = "event_type"),
    @Index(name = "idx_security_audit_timestamp", columnList = "timestamp"),
    @Index(name = "idx_security_audit_ip", columnList = "ip_address")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "user_email", length = 255)
    private String userEmail;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "details", length = 1000)
    private String details;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
}
