package org.aldousdev.dockflowbackend.ai.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.ai.config.AiRabbitConfig;
import org.aldousdev.dockflowbackend.ai.dto.AiTaskDto;
import org.aldousdev.dockflowbackend.auth.security.ServiceJwtTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiTaskProducer {
    private final RabbitTemplate aiRabbitTemplate;
    private final ServiceJwtTokenService serviceJwtTokenService;
    private final org.aldousdev.dockflowbackend.chat.repository.ChatChannelRepository chatChannelRepository;
    private final org.aldousdev.dockflowbackend.chat.repository.MessageRepository messageRepository;
    
    @Value("${app.ai.url:http://localhost:8080}")
    private String internalBaseUrl;

    public void sendDocumentAnalyze(Long documentId, Long companyId){
        sendDocumentAnalyze(documentId, null, null, null, null, null, companyId, null, null);
    }
    
    public void sendDocumentAnalyze(
            Long documentId, 
            Long versionId,
            String fileUrl,
            String fileName,
            String mimeType,
            Long fileSize,
            Long companyId,
            String correlationId,
            String provider) {
        
        AiTaskDto aiTaskDto = new AiTaskDto();
        aiTaskDto.setType("DOCUMENT_ANALYZE");
        aiTaskDto.setCorrelationId(correlationId != null ? correlationId : "doc-" + documentId + "-" + System.currentTimeMillis());
        aiTaskDto.setCreatedAt(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString());
        aiTaskDto.setReplyTo(AiRabbitConfig.CORE_RESULTS_QUEUE);

        String serviceToken = serviceJwtTokenService.generateToken();
        
        String internalFileUrl;
        if (versionId != null) {
            internalFileUrl = internalBaseUrl + "/api/internal/documents/" + documentId + "/versions/" + versionId + "/download?companyId=" + companyId;
        } else {
            internalFileUrl = internalBaseUrl + "/api/internal/documents/" + documentId + "/download?companyId=" + companyId;
        }

        aiTaskDto.getPayload().put("document_id", documentId);
        if (versionId != null) {
            aiTaskDto.getPayload().put("version_id", versionId);
        }
        aiTaskDto.getPayload().put("file_url", internalFileUrl);
        aiTaskDto.getPayload().put("service_token", serviceToken);
        
        if (fileName != null) {
            aiTaskDto.getPayload().put("file_name", fileName);
        }
        if (mimeType != null) {
            aiTaskDto.getPayload().put("mime_type", mimeType);
        }
        if (fileSize != null) {
            aiTaskDto.getPayload().put("file_size", fileSize);
        }
        aiTaskDto.getPayload().put("company_id", companyId);
        aiTaskDto.getPayload().put("priority", "normal");
        
        if (provider != null) {
            aiTaskDto.getPayload().put("provider", provider);
        }

        log.info("Sending AI task [{}]: correlationId={}, routingKey={}, fileUrl={}", 
            aiTaskDto.getType(), aiTaskDto.getCorrelationId(), AiRabbitConfig.AI_TASK_QUEUE, internalFileUrl);
            
        aiRabbitTemplate.convertAndSend(AiRabbitConfig.AI_TASK_QUEUE, aiTaskDto);
    }

    public void sendChatMessage(String content, Long channelId, Long senderId, String senderName, String chatType) {
        sendChatMessage(content, channelId, senderId, senderName, null, null, null, null, null, null, chatType);
    }

    public void sendChatMessage(
            String content, 
            Long channelId, 
            Long senderId, 
            String senderName, 
            Long documentId, 
            Long versionId,
            String fileName,
            String mimeType,
            Long fileSize,
            Long companyId,
            String chatType) {
        
        AiTaskDto aiTaskDto = new AiTaskDto();
        aiTaskDto.setType("CHAT");
        aiTaskDto.setCorrelationId("chat-" + channelId + "-" + System.currentTimeMillis());
        aiTaskDto.setCreatedAt(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString());
        aiTaskDto.setReplyTo(AiRabbitConfig.CORE_RESULTS_QUEUE);

        aiTaskDto.getPayload().put("content", content);
        aiTaskDto.getPayload().put("channel_id", channelId);
        aiTaskDto.getPayload().put("sender_id", senderId);
        aiTaskDto.getPayload().put("sender_name", senderName);

        if (documentId != null) {
            aiTaskDto.getPayload().put("document_id", documentId);
            if (versionId != null) {
                aiTaskDto.getPayload().put("version_id", versionId);
            }
            
            // Generate token and build internal file URL if needed
            aiTaskDto.getPayload().put("service_token", serviceJwtTokenService.generateToken());
            String internalFileUrl;
            if (versionId != null) {
                internalFileUrl = internalBaseUrl + "/api/internal/documents/" + documentId + "/versions/" + versionId + "/download?companyId=" + companyId;
            } else {
                internalFileUrl = internalBaseUrl + "/api/internal/documents/" + documentId + "/download?companyId=" + companyId;
            }
            aiTaskDto.getPayload().put("file_url", internalFileUrl);
            if (fileName != null) aiTaskDto.getPayload().put("file_name", fileName);
            if (mimeType != null) aiTaskDto.getPayload().put("mime_type", mimeType);
            if (fileSize != null) aiTaskDto.getPayload().put("file_size", fileSize);
        }

        if (chatType != null) {
            aiTaskDto.getPayload().put("chat_type", chatType);
        }

        // Fetch history (last 50 messages)
        try {
            chatChannelRepository.findById(channelId).ifPresent(channel -> {
                org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 50, org.springframework.data.domain.Sort.by("createdAt").descending());
                org.springframework.data.domain.Page<org.aldousdev.dockflowbackend.chat.entity.Message> historyPage = messageRepository.findByChannelPaginated(channel, pageable);
                
                java.util.List<java.util.Map<String, String>> history = historyPage.getContent().stream()
                    .sorted(java.util.Comparator.comparing(org.aldousdev.dockflowbackend.chat.entity.Message::getCreatedAt))
                    .map(m -> {
                        java.util.Map<String, String> msgMap = new java.util.HashMap<>();
                        msgMap.put("role", m.getSender().getEmail().equals("ai@dockflow.com") ? "assistant" : "user");
                        msgMap.put("sender", m.getSender().getFirstName());
                        msgMap.put("content", m.getContent());
                        return msgMap;
                    })
                    .collect(java.util.stream.Collectors.toList());
                
                aiTaskDto.getPayload().put("history", history);
                log.info("Included {} messages from history for channel {}", history.size(), channelId);
            });
        } catch (Exception e) {
            log.error("Failed to fetch chat history for AI task", e);
        }

        log.info("Sending AI task [CHAT]: correlationId={}, routingKey={}, withDocument={}", 
            aiTaskDto.getCorrelationId(), AiRabbitConfig.AI_TASK_QUEUE, documentId != null);

        aiRabbitTemplate.convertAndSend(AiRabbitConfig.AI_TASK_QUEUE, aiTaskDto);
    }

    public void sendDocumentReview(
            Long documentId, 
            Long versionId,
            String fileName,
            String mimeType,
            Long fileSize,
            Long companyId,
            String correlationId,
            String provider,
            String topic) {
        
        AiTaskDto aiTaskDto = new AiTaskDto();
        aiTaskDto.setType("DOCUMENT_REVIEW");
        aiTaskDto.setCorrelationId(correlationId != null ? correlationId : "rev-" + documentId + "-" + System.currentTimeMillis());
        aiTaskDto.setCreatedAt(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString());
        aiTaskDto.setReplyTo(AiRabbitConfig.CORE_RESULTS_QUEUE);

        String serviceToken = serviceJwtTokenService.generateToken();
        
        String internalFileUrl = internalBaseUrl + "/api/internal/documents/" + documentId + "/versions/" + versionId + "/download?companyId=" + companyId;

        aiTaskDto.getPayload().put("document_id", documentId);
        aiTaskDto.getPayload().put("version_id", versionId);
        aiTaskDto.getPayload().put("file_url", internalFileUrl);
        aiTaskDto.getPayload().put("service_token", serviceToken);
        aiTaskDto.getPayload().put("file_name", fileName);
        aiTaskDto.getPayload().put("mime_type", mimeType);
        aiTaskDto.getPayload().put("file_size", fileSize);
        aiTaskDto.getPayload().put("company_id", companyId);
        aiTaskDto.getPayload().put("priority", "high");
        
        if (provider != null) {
            aiTaskDto.getPayload().put("provider", provider);
        }
        if (topic != null) {
            aiTaskDto.getPayload().put("topic", topic);
        }

        log.info("Sending AI document review task: correlationId={}, topic={}", 
            aiTaskDto.getCorrelationId(), topic);
            
        aiRabbitTemplate.convertAndSend(AiRabbitConfig.AI_TASK_QUEUE, aiTaskDto);
    }
}
