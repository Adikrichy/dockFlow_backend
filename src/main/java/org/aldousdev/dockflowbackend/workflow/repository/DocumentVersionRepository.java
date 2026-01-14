package org.aldousdev.dockflowbackend.workflow.repository;

import org.aldousdev.dockflowbackend.workflow.entity.Document;
import org.aldousdev.dockflowbackend.workflow.entity.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository для работы с версиями документов
 */
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {

    @Query("SELECT dv FROM DocumentVersion dv WHERE dv.document.id = :documentId ORDER BY dv.versionNumber DESC")
    List<DocumentVersion> findByDocumentIdOrderByVersionNumberDesc(@Param("documentId") Long documentId);

    @Query("SELECT dv FROM DocumentVersion dv WHERE dv.document.id = :documentId AND dv.isCurrent = true")
    Optional<DocumentVersion> findCurrentVersionByDocumentId(@Param("documentId") Long documentId);

    @Query("SELECT dv FROM DocumentVersion dv WHERE dv.sha256Hash = :hash AND dv.document.company.id = :companyId")
    Optional<DocumentVersion> findBySha256HashAndCompanyId(@Param("hash") String hash, @Param("companyId") Long companyId);

    @Query("SELECT MAX(dv.versionNumber) FROM DocumentVersion dv WHERE dv.document.id = :documentId")
    Optional<Integer> findMaxVersionNumberByDocumentId(@Param("documentId") Long documentId);

    @Query("SELECT dv FROM DocumentVersion dv WHERE dv.document.id = :documentId AND dv.versionNumber = :versionNumber")
    Optional<DocumentVersion> findByDocumentIdAndVersionNumber(@Param("documentId") Long documentId, @Param("versionNumber") Integer versionNumber);

    @Query("SELECT COUNT(dv) FROM DocumentVersion dv WHERE dv.document.id = :documentId")
    long countByDocumentId(@Param("documentId") Long documentId);

    @Query("SELECT dv FROM DocumentVersion dv WHERE dv.document.id = :documentId AND dv.isSigned = true ORDER BY dv.createdAt DESC")
    List<DocumentVersion> findSignedVersionsByDocumentId(@Param("documentId") Long documentId);

    @Query("SELECT COUNT(dv) FROM DocumentVersion dv WHERE dv.createdAt BETWEEN :startDate AND :endDate")
    long countByCreatedAtBetween(@Param("startDate") java.time.LocalDateTime startDate, @Param("endDate") java.time.LocalDateTime endDate);

    @Query("SELECT COUNT(dv) FROM DocumentVersion dv JOIN dv.document d WHERE d.company.id = :companyId AND dv.createdAt BETWEEN :startDate AND :endDate")
    long countByCompanyIdAndCreatedAtBetween(@Param("companyId") Long companyId, @Param("startDate") java.time.LocalDateTime startDate, @Param("endDate") java.time.LocalDateTime endDate);
}
