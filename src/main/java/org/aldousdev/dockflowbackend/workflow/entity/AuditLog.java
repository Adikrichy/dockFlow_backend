package org.aldousdev.dockflowbackend.workflow.entity;

import jakarta.persistence.*;
import lombok.*;
import org.aldousdev.dockflowbackend.auth.entity.User;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "audit_logs")
@Setter
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false)
    private String action;

    private String details;

    @ManyToOne(fetch = FetchType.LAZY)
    private User performedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    private Document document;

    private LocalDateTime timestamp = LocalDateTime.now();
}
