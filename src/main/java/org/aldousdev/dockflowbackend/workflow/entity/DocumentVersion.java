package org.aldousdev.dockflowbackend.workflow.entity;

import jakarta.persistence.*;
import lombok.*;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Entity для хранения версий документов
 * Каждая версия содержит отдельный файл и метаданные изменений
 */
@Entity
@Table(name = "document_versions", indexes = {
    @Index(name = "idx_document_versions_document", columnList = "document_id"),
    @Index(name = "idx_document_versions_number", columnList = "document_id, version_number"),
    @Index(name = "idx_document_versions_created", columnList = "document_id, created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class DocumentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(nullable = false)
    private String filePath;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "content_type")
    private String contentType = "application/pdf";

    @Column(name = "file_size")
    private Long fileSize;

    @Column(length = 64)
    private String sha256Hash;

    @Column(length = 500)
    private String changeDescription;

    @Column(length = 100)
    private String changeType; // "UPLOAD", "EDIT", "SIGN", "WATERMARK", etc.

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Metadata for workflow context (JSON)
    @Column(columnDefinition = "TEXT")
    private String workflowMetadata;

    // Флаги для специальных версий
    @Builder.Default
    @Column(name = "is_signed")
    private Boolean isSigned = false;

    @Builder.Default
    @Column(name = "has_watermark")
    private Boolean hasWatermark = false;

    @Builder.Default
    @Column(name = "is_current")
    private Boolean isCurrent = false;

    /**
     * Получить полный путь к файлу версии
     */
    public String getFullFilePath() {
        return filePath;
    }

    /**
     * Проверить, является ли версия текущей
     */
    public boolean isCurrentVersion() {
        return Boolean.TRUE.equals(isCurrent);
    }

    /**
     * Проверить, была ли версия подписана
     */
    public boolean isSignedVersion() {
        return Boolean.TRUE.equals(isSigned);
    }
}
