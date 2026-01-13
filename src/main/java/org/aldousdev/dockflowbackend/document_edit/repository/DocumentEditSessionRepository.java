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

    @Query("SELECT s FROM DocumentEditSession s WHERE s.document.id = :documentId AND s.status = :status ORDER BY s.createdAt DESC")
    List<DocumentEditSession> findByDocumentIdAndStatus(
            @Param("documentId") Long documentId,
            @Param("status") EditSessionStatus status
    );
}
