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
import org.aldousdev.dockflowbackend.chat.repository.ChatChannelRepository;
import org.aldousdev.dockflowbackend.chat.repository.MessageRepository;
import org.aldousdev.dockflowbackend.chat.service.ChatService;
import org.aldousdev.dockflowbackend.chat.dto.ChatMessageDTO;
import org.aldousdev.dockflowbackend.chat.entity.ChatChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
    private final ChatService chatService;
    private final ChatChannelRepository chatChannelRepository;
    private final MessageRepository messageRepository;
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    /**
     * Start AI analysis for a specific document version.
     *
     * IMPORTANT:
     * - versionId here is the PRIMARY KEY of documents_versions.id (not version_number).
     */
    public AiAnalysisResponse startDocumentAnalysis(Long documentId, Long versionId, String provider) {
        User user = getCurrentUser();

        log.info("Starting AI analysis: documentId={}, versionId={}, provider={}, user={}",
                documentId,
                versionId,
                provider != null ? provider : "default",
                user.getEmail()
        );

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        Long companyId = document.getCompany().getId();

        // Load DocumentVersion (with fallback)
        DocumentVersion version;
        if (versionId == null || versionId.equals(documentId) || versionId <= 0) {
            log.info("No valid versionId provided for analysis, falling back to current version for document {}", documentId);
            version = documentVersionRepository.findCurrentVersionByDocumentId(documentId)
                    .orElseThrow(() -> new RuntimeException("Current version not found for document " + documentId));
        } else {
            version = documentVersionRepository.findByIdAndDocumentId(versionId, documentId)
                    .orElseThrow(() -> new RuntimeException("Version " + versionId + " not found for document " + documentId));
        }
        final Long actualVersionId = version.getId();

        String correlationId = String.format("doc-analyze-%d-%d-%d",
                documentId, actualVersionId, System.currentTimeMillis());

        DocumentAiAnalysis analysis = analysisRepository.findByDocumentIdAndVersionId(documentId, actualVersionId)
                .orElse(new DocumentAiAnalysis());

        analysis.setDocumentId(documentId);
        analysis.setVersionId(actualVersionId);
        analysis.setCompanyId(companyId);
        analysis.setCorrelationId(correlationId);
        analysis.setStatus("PENDING");
        analysis.setSummary(null);
        analysis.setRawResult(null);
        analysis.setError(null);

        analysis = analysisRepository.save(analysis);

        aiTaskProducer.sendDocumentAnalyze(
                documentId,
                actualVersionId,
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

    public AiAnalysisResponse startDocumentReview(Long documentId, Long versionId, String provider, String topic) {
        User user = getCurrentUser();

        log.info("Starting AI document review: documentId={}, versionId={}, provider={}, topic={}, user={}",
                documentId, versionId, provider != null ? provider : "default", topic, user.getEmail());

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        Long companyId = document.getCompany().getId();

        // Load DocumentVersion (with fallback)
        DocumentVersion version;
        if (versionId == null || versionId.equals(documentId) || versionId <= 0) {
            log.info("No valid versionId provided for review, falling back to current version for document {}", documentId);
            version = documentVersionRepository.findCurrentVersionByDocumentId(documentId)
                    .orElseThrow(() -> new RuntimeException("Current version not found for document " + documentId));
        } else {
            version = documentVersionRepository.findByIdAndDocumentId(versionId, documentId)
                    .orElseThrow(() -> new RuntimeException("Version " + versionId + " not found for document " + documentId));
        }
        final Long actualVersionId = version.getId();

        String correlationId = String.format("rev-%d-%d-%d",
                documentId, actualVersionId, System.currentTimeMillis());

        DocumentAiAnalysis analysis = analysisRepository.findByDocumentIdAndVersionId(documentId, actualVersionId)
                .orElse(new DocumentAiAnalysis());

        analysis.setDocumentId(documentId);
        analysis.setVersionId(actualVersionId);
        analysis.setCompanyId(companyId);
        analysis.setCorrelationId(correlationId);
        analysis.setStatus("PENDING");
        analysis.setSummary(null);
        analysis.setRawResult(null);
        analysis.setError(null);

        analysis = analysisRepository.save(analysis);

        aiTaskProducer.sendDocumentReview(
                documentId,
                actualVersionId,
                version.getOriginalFilename(),
                version.getContentType(),
                version.getFileSize(),
                companyId,
                correlationId,
                provider,
                topic
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

    @Transactional
    public ChatMessageDTO sendDocumentChatMessage(Long documentId, Long versionId, String content) {
        User user = getCurrentUser();
        var document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        
        // Load DocumentVersion metadata (with fallback)
        DocumentVersion version;
        if (versionId == null || versionId.equals(documentId) || versionId <= 0) {
            log.info("No valid versionId provided for chat, falling back to current version for document {}", documentId);
            version = documentVersionRepository.findCurrentVersionByDocumentId(documentId)
                    .orElseThrow(() -> new RuntimeException("Current version not found for document " + documentId));
        } else {
            version = documentVersionRepository.findByIdAndDocumentId(versionId, documentId)
                    .orElseThrow(() -> new RuntimeException("Version " + versionId + " not found for document " + documentId));
        }

        final Long finalVersionId = version.getId();

        // Find or create a hidden channel for this document
        String channelName = "AI-Chat-Doc-" + documentId;
        var company = document.getCompany();
        
        ChatChannel channel = chatChannelRepository.findByNameAndCompany(channelName, company)
                .orElseGet(() -> {
                    ChatChannel newChannel = ChatChannel.builder()
                            .name(channelName)
                            .description("AI Document Discussion: " + document.getOriginalFilename())
                            .company(company)
                            .isPublic(false) // Keep it hidden from general list
                            .type(ChatChannel.ChannelType.CHANNEL)
                            .members(java.util.List.of(user))
                            .build();
                    return chatChannelRepository.save(newChannel);
                });

        // Save user message
        ChatMessageDTO userMessage = chatService.saveMessage(channel.getId(), content, user);
        userMessage.setAi(false);
        
        // Broadcast user message to channel
        messagingTemplate.convertAndSend("/topic/channel/" + channel.getId(), userMessage);
        
        // Send task to AI with full context
        aiTaskProducer.sendChatMessage(
                content, 
                channel.getId(), 
                user.getId(), 
                user.getFirstName() + " " + user.getLastName(), 
                documentId, 
                finalVersionId, 
                version.getOriginalFilename(),
                version.getContentType(),
                version.getFileSize(),
                company.getId(),
                "DOCUMENT"
        );

        return userMessage;
    }

    @Transactional(readOnly = true)
    public java.util.List<ChatMessageDTO> getDocumentChatHistory(Long documentId) {
        var document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        
        String channelName = "AI-Chat-Doc-" + documentId;
        var company = document.getCompany();
        
        return chatChannelRepository.findByNameAndCompany(channelName, company)
                .map(channel -> {
                    return messageRepository.findByChannel(channel).stream()
                            .map(m -> ChatMessageDTO.builder()
                                     .id(m.getId())
                                     .content(m.getContent())
                                     .senderId(m.getSender().getId())
                                     .senderName(m.getSender().getFirstName() + " " + m.getSender().getLastName())
                                     .channelId(channel.getId())
                                     .timestamp(m.getCreatedAt())
                                     .type("CHAT")
                                     .isAi(m.getSender().getEmail().equals("ai@dockflow.com"))
                                     .build())
                            .toList();
                }).orElse(java.util.Collections.emptyList());
    }
}
