package org.aldousdev.dockflowbackend.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.ai.dto.AiAnalysisResponse;
import org.aldousdev.dockflowbackend.ai.entity.DocumentAiAnalysis;
import org.aldousdev.dockflowbackend.ai.producer.AiTaskProducer;
import org.aldousdev.dockflowbackend.ai.repository.DocumentAiAnalysisRepository;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.auth.service.AuthService;
import org.aldousdev.dockflowbackend.workflow.entity.Document;
import org.aldousdev.dockflowbackend.workflow.entity.DocumentVersion;
import org.aldousdev.dockflowbackend.workflow.repository.DocumentRepository;
import org.aldousdev.dockflowbackend.workflow.repository.DocumentVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DocumentAiAnalysisService {

    private final AiTaskProducer aiTaskProducer;
    private final DocumentAiAnalysisRepository analysisRepository;

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;

    private final AuthService authService;

    /**
     * Start AI analysis for a specific document version.
     *
     * IMPORTANT:
     * - versionId here is the PRIMARY KEY of documents_versions.id (not version_number).
     * - In your DB example: documents_versions.id=47, version_number=2, document_id=302
     */
    public AiAnalysisResponse startDocumentAnalysis(Long documentId, Long versionId, String provider) {
        User user = getCurrentUser();

        log.info("Starting AI analysis: documentId={}, versionId={}, provider={}, user={}",
                documentId,
                versionId,
                provider != null ? provider : "default",
                user.getEmail()
        );

        // 1) Load document (you can later enforce company scope here)
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        Long companyId = document.getCompany().getId();

        log.info("AI analyze request: documentId={}, versionId={}, companyId={}", documentId, versionId, companyId);

        // 2) Load DocumentVersion by PK AND verify it belongs to this document
        // This avoids the classic confusion: versionId (PK) vs versionNumber (logical version)
        DocumentVersion version = documentVersionRepository.findByIdAndDocumentId(versionId, documentId)
                .orElseThrow(() -> new RuntimeException("Version not found"));

        // 3) Create unique correlationId for async processing (RabbitMQ)
        String correlationId = String.format("doc-analyze-%d-%d-%d",
                documentId, versionId, System.currentTimeMillis());

        // 4) Create DB record first (or update existing one)
        // This avoids uq_doc_version unique constraint violation
        DocumentAiAnalysis analysis = analysisRepository.findByDocumentIdAndVersionId(documentId, versionId)
                .orElse(new DocumentAiAnalysis());

        analysis.setDocumentId(documentId);
        analysis.setVersionId(versionId);
        analysis.setCompanyId(companyId);
        analysis.setCorrelationId(correlationId);
        analysis.setStatus("PENDING");
        analysis.setSummary(null);
        analysis.setRawResult(null);
        analysis.setError(null);

        analysis = analysisRepository.save(analysis);

        // 5) Send task to AI service via RabbitMQ
        // Producer generates internal URL and service JWT token
        aiTaskProducer.sendDocumentAnalyze(
                documentId,
                versionId,
                null,
                version.getOriginalFilename(),
                version.getContentType(),
                version.getFileSize(),
                companyId,
                correlationId,
                provider
        );

        return mapToResponse(analysis);
    }

    /**
     * Returns saved analysis status/result for (documentId, versionId(PK)).
     */
    @Transactional(readOnly = true)
    public AiAnalysisResponse getAnalysisResult(Long documentId, Long versionId) {
        DocumentAiAnalysis analysis = analysisRepository.findByDocumentIdAndVersionId(documentId, versionId)
                .orElseThrow(() -> new RuntimeException("Analysis not found"));

        return mapToResponse(analysis);
    }

    private AiAnalysisResponse mapToResponse(DocumentAiAnalysis entity) {
        return AiAnalysisResponse.builder()
                .documentId(entity.getDocumentId())
                .versionId(entity.getVersionId())
                .status(entity.getStatus())
                .summary(entity.getSummary())
                .rawResult(entity.getRawResult())
                .error(entity.getError())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private User getCurrentUser() {
        return authService.getCurrentUser();
    }
}
