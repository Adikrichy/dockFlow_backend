package org.aldousdev.dockflowbackend.ai.repository;

import org.aldousdev.dockflowbackend.ai.entity.DocumentAiAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentAiAnalysisRepository extends JpaRepository<DocumentAiAnalysis, Long> {
    Optional<DocumentAiAnalysis> findTopByDocumentIdOrderByCreatedAtDesc(Long documentId);
    Optional<DocumentAiAnalysis> findByCorrelationId(String correlationId);
    Optional<DocumentAiAnalysis> findByDocumentIdAndVersionId(Long documentId, Long versionId);
}
