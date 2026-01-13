package org.aldousdev.dockflowbackend.document_edit.entity;

import jakarta.persistence.*;
import lombok.*;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.document_edit.enums.EditSessionStatus;
import org.aldousdev.dockflowbackend.workflow.entity.Document;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_edit_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentEditSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "session_key", nullable = false, unique = true, length = 64)
    private String sessionKey;

    @Column(name = "onlyoffice_key", nullable = false, unique = true, length = 128)
    private String onlyofficeKey;

    @Column(name = "working_docx_path", nullable = false, length = 1000)
    private String workingDocxPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EditSessionStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "committed_at")
    private LocalDateTime committedAt;
}
