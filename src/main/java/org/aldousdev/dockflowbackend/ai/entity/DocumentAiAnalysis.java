package org.aldousdev.dockflowbackend.ai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_ai_analysis", schema = "ai",
        indexes = {
                @Index(name = "idx_doc_ai_document_id", columnList = "documentId"),
                @Index(name = "idx_doc_ai_version_id", columnList = "versionId"),
                @Index(name = "idx_doc_ai_company_id", columnList = "companyId"),
                @Index(name = "idx_doc_ai_correlation_id", columnList = "correlationId")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentAiAnalysis {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private Long documentId;
    private Long versionId;
    private Long companyId;

    private String correlationId;

    @Column(length = 32)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String rawResult;

    @Column(columnDefinition = "TEXT")
    private String error;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist(){
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate(){
        updatedAt = LocalDateTime.now();
    }
}
