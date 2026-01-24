package org.aldousdev.dockflowbackend.ai.service;

import lombok.RequiredArgsConstructor;
import org.aldousdev.dockflowbackend.ai.entity.DocumentAiAnalysis;
import org.aldousdev.dockflowbackend.ai.producer.AiTaskProducer;
import org.aldousdev.dockflowbackend.ai.repository.DocumentAiAnalysisRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiFacade {

    private final AiTaskProducer producer;
    private final DocumentAiAnalysisRepository repo;

    @Transactional
    public void requestDocumentAnalyze(Long documentId, Long companyId) {
        String correlationId = "doc-" + documentId;

        // создаём PENDING запись (чтобы фронт видел “анализируется”)
        repo.save(DocumentAiAnalysis.builder()
                .documentId(documentId)
                .companyId(companyId)
                .correlationId(correlationId)
                .status("PENDING")
                .build());

        producer.sendDocumentAnalyze(documentId, companyId);
    }
}

