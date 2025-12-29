package org.aldousdev.dockflowbackend.workflow.entity;

import jakarta.persistence.*;
import lombok.*;
import org.aldousdev.dockflowbackend.auth.entity.Company;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.workflow.enums.DocumentStatus;
import org.aldousdev.dockflowbackend.workflow.enums.DocumentType;
import org.aldousdev.dockflowbackend.workflow.enums.Priority;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(name = "documents")
@Builder
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false)
    private String originalFilename;

    @Column(nullable = false)
    private String filePath;

    private String contentType = "application/pdf";

    private Long fileSize;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @CreatedDate
    @Column(updatable = false, nullable = false)
    private LocalDateTime uploadedAt;

    @Builder.Default
    private Boolean signed = false;

    // Fields for conditional routing
    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    private Priority priority = Priority.NORMAL;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    private DocumentType documentType = DocumentType.GENERAL;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    private DocumentStatus status = DocumentStatus.DRAFT;

    // Metadata for extensibility (JSON field for additional conditions)
    @Column(columnDefinition = "TEXT")
    private String metadata;

}
