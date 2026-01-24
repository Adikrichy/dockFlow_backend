package org.aldousdev.dockflowbackend.document_edit.repository;

import org.aldousdev.dockflowbackend.document_edit.entity.DocumentEditSession;
import org.aldousdev.dockflowbackend.document_edit.enums.EditSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DocumentEditSessionRepository extends JpaRepository<DocumentEditSession, Long> {

    Optional<DocumentEditSession> findBySessionKey(String sessionKey);

    Optional<DocumentEditSession> findByOnlyofficeKey(String onlyofficeKey);

    List<DocumentEditSession> findByDocumentIdAndStatus(
            @Param("documentId") Long documentId,
            @Param("status") EditSessionStatus status
    );

    @Query("SELECT s FROM DocumentEditSession s WHERE s.document.id = :documentId AND s.baseVersionNumber = :versionNumber AND s.status = :status ORDER BY s.createdAt DESC")
    List<DocumentEditSession> findByDocumentIdAndBaseVersionNumberAndStatus(
            @Param("documentId") Long documentId,
            @Param("versionNumber") Integer versionNumber,
            @Param("status") EditSessionStatus status
    );

    @Query("SELECT s FROM DocumentEditSession s WHERE s.document.id = :documentId AND s.baseVersionNumber = :versionNumber AND s.createdBy.id = :userId AND s.status = :status ORDER BY s.createdAt DESC")
    List<DocumentEditSession> findByDocumentIdAndBaseVersionNumberAndUserIdAndStatus(
            @Param("documentId") Long documentId,
            @Param("versionNumber") Integer versionNumber,
            @Param("userId") Long userId,
            @Param("status") EditSessionStatus status
    );
}
