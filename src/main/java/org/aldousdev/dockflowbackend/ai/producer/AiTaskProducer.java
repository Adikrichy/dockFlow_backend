package org.aldousdev.dockflowbackend.ai.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.ai.config.AiRabbitConfig;
import org.aldousdev.dockflowbackend.ai.dto.AiTaskDto;
import org.aldousdev.dockflowbackend.auth.security.ServiceJwtTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiTaskProducer {
    private final RabbitTemplate aiRabbitTemplate;
    private final ServiceJwtTokenService serviceJwtTokenService;
    
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
}
