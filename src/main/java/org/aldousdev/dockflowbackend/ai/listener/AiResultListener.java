package org.aldousdev.dockflowbackend.ai.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.ai.config.AiRabbitConfig;
import org.aldousdev.dockflowbackend.ai.dto.AiResultDto;
import org.aldousdev.dockflowbackend.ai.repository.DocumentAiAnalysisRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiResultListener {

    private final DocumentAiAnalysisRepository repo;
    private final org.aldousdev.dockflowbackend.ai.repository.ReportAiAnalysisRepository reportAiRepo;
    private final org.aldousdev.dockflowbackend.chat.service.ChatService chatService;
    private final org.aldousdev.dockflowbackend.auth.repository.UserRepository userRepository;
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    @RabbitListener(queues = AiRabbitConfig.CORE_RESULTS_QUEUE)
    @Transactional
    public void onResult(AiResultDto result) {
        log.info("AI result received: correlation_id={}, status={}",
                result.getCorrelationId(), result.getStatus());

        repo.findByCorrelationId(result.getCorrelationId()).ifPresentOrElse(entity -> {

            if("PROCESSING".equalsIgnoreCase(result.getStatus())){
                entity.setStatus(result.getStatus());
                repo.save(entity);
                return;
            }

            if ("SUCCESS".equalsIgnoreCase(result.getStatus()) || "OK".equalsIgnoreCase(result.getStatus())) {
                log.info("Processing successful AI result for correlation_id={}", result.getCorrelationId());
                
                // Extract summary from result
                Object summaryObj = result.getResult() != null ? result.getResult().get("summary") : null;
                if (summaryObj instanceof java.util.List<?>) {
                    java.util.List<?> list = (java.util.List<?>) summaryObj;
                    java.util.List<String> stringList = new java.util.ArrayList<>();
                    for (Object item : list) {
                        if (item != null) stringList.add(item.toString());
                    }
                    entity.setSummary(String.join("; ", stringList));
                } else {
                    entity.setSummary(summaryObj != null ? summaryObj.toString() : null);
                }
                entity.setError(null);
                
                // Store full result as JSON string
                if (result.getResult() != null) {
                    try {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        String detailedResult = mapper.writeValueAsString(result.getResult());
                        entity.setRawResult(detailedResult);
                        log.debug("Serialized raw_result: {}", detailedResult);
                    } catch (Exception e) {
                        log.error("Failed to serialize detailed result for correlation_id={}", result.getCorrelationId(), e);
                    }
                } else {
                    log.warn("Result payload is null for successful AI task: correlation_id={}", result.getCorrelationId());
                }

                // Set status LAST to ensure frontend doesn't see SUCCESS until data is ready
                entity.setStatus("SUCCESS");
            } else if("ERROR".equalsIgnoreCase(result.getStatus())) {
                String errorMsg = result.getError();
                log.error("AI analysis error for correlation_id={}: {}", result.getCorrelationId(), errorMsg);
                
                // Truncate error if too long to ensure it saves
                if (errorMsg != null && errorMsg.length() > 5000) {
                    errorMsg = errorMsg.substring(0, 5000) + "... [TRUNCATED]";
                }
                
                entity.setStatus("ERROR");
                entity.setError(errorMsg);
            }

            try {
                repo.save(entity);
            } catch (Exception e) {
                log.error("Failed to save analysis result for correlation_id={}", result.getCorrelationId(), e);
                // Try to save just the error status if allow
                try {
                     entity.setStatus("ERROR");
                     entity.setError("Database save failed: " + e.getMessage());
                     entity.setRawResult(null);
                     entity.setSummary(null);
                     repo.save(entity);
                } catch (Exception ex) {
                     log.error("CRITICAL: Failed to recover save for correlation_id={}", result.getCorrelationId(), ex);
                }
            }
        }, () -> {
            if ("CHAT_RESPONSE".equalsIgnoreCase(result.getStatus())) {
                handleChatResponse(result);
            } else if ("WORKFLOW_SUGGEST_RESPONSE".equalsIgnoreCase(result.getStatus())) {
                handleWorkflowSuggestResponse(result);
            } else if ("REPORT_INSIGHTS_RESPONSE".equalsIgnoreCase(result.getStatus())) {
                handleReportInsightsResponse(result);
            } else if ("ERROR".equalsIgnoreCase(result.getStatus()) && result.getCorrelationId() != null) {
                if (result.getCorrelationId().startsWith("chat-")) {
                    handleChatError(result);
                } else if (result.getCorrelationId().startsWith("wf-suggest-")) {
                    handleWorkflowSuggestError(result);
                } else if (result.getCorrelationId().startsWith("report-")) {
                    handleReportError(result);
                }
            } else {
                // Ignore "PROCESSING" status for records we don't track in DocumentAiAnalysis (like general chat)
                if (!"PROCESSING".equalsIgnoreCase(result.getStatus())) {
                    log.warn("No DocumentAiAnalysis row found for correlation_id={}", result.getCorrelationId());
                }
            }
        });
    }

    private void handleReportInsightsResponse(AiResultDto result) {
        try {
            log.info("Handling REPORT_INSIGHTS_RESPONSE for correlation_id={}", result.getCorrelationId());
            String correlationId = result.getCorrelationId();
            
            reportAiRepo.findByCorrelationId(correlationId).ifPresent(entity -> {
                java.util.Map<String, Object> data = result.getResult();
                if (data != null && data.get("insights") != null) {
                    entity.setInsights(data.get("insights").toString());
                    entity.setStatus("SUCCESS");
                    reportAiRepo.save(entity);
                    log.info("Report AI insights saved: correlation_id={}", correlationId);
                } else {
                    log.warn("Report AI result has no 'insights' payload for correlation_id={}", correlationId);
                }
            });
        } catch (Exception e) {
            log.error("Failed to process REPORT_INSIGHTS_RESPONSE", e);
        }
    }

    private void handleReportError(AiResultDto result) {
        try {
            log.info("Handling REPORT ERROR for correlation_id={}", result.getCorrelationId());
            reportAiRepo.findByCorrelationId(result.getCorrelationId()).ifPresent(entity -> {
                entity.setStatus("ERROR");
                entity.setInsights("System Error: AI service failed to provide insights.");
                reportAiRepo.save(entity);
            });
        } catch (Exception e) {
            log.error("Failed to process REPORT ERROR", e);
        }
    }

    private void handleChatError(AiResultDto result) {
        try {
            log.info("Handling CHAT ERROR for correlation_id={}", result.getCorrelationId());
            // Extract channelId from correlationId: "chat-{channelId}-{timestamp}"
            String[] parts = result.getCorrelationId().split("-");
            if (parts.length >= 2) {
                Long channelId = Long.valueOf(parts[1]);
                
                org.aldousdev.dockflowbackend.chat.dto.ChatMessageDTO errorDto = new org.aldousdev.dockflowbackend.chat.dto.ChatMessageDTO();
                errorDto.setChannelId(channelId);
                errorDto.setContent("AI Error: " + (result.getError() != null ? result.getError() : "Unknown AI failure"));
                errorDto.setAi(true);
                errorDto.setStatus("error");
                errorDto.setTimestamp(java.time.LocalDateTime.now());

                // Push to WebSocket so frontend knows to stop "thinking"
                messagingTemplate.convertAndSend("/topic/channel/" + channelId, errorDto);
                log.info("Sent AI error notification to channel {}", channelId);
            }
        } catch (Exception e) {
            log.error("Failed to process CHAT ERROR", e);
        }
    }

    private void handleChatResponse(AiResultDto result) {
        try {
            log.info("Handling CHAT_RESPONSE: {}", result);
            java.util.Map<String, Object> data = result.getResult();
            if (data == null) {
                log.error("Chat response has no data");
                return;
            }

            Long channelId = Long.valueOf(data.get("channel_id").toString());
            String responseText = (String) data.get("response");
            
            // AI User (we need to find it or create a placeholder)
            // For now, let's look up by email or use a system user
            org.aldousdev.dockflowbackend.auth.entity.User aiUser = userRepository.findByEmail("ai@dockflow.com")
                    .orElseThrow(() -> new RuntimeException("AI User not found"));

            // Save message
            org.aldousdev.dockflowbackend.chat.dto.ChatMessageDTO messageDto = 
                    chatService.saveMessage(channelId, responseText, aiUser);
            messageDto.setAi(true);
            messageDto.setStatus("sent");

            // Push to WebSocket
            log.info("Broadcasting AI message to /topic/channel/{}", channelId);
            messagingTemplate.convertAndSend("/topic/channel/" + channelId, messageDto);

        } catch (Exception e) {
            log.error("Failed to process CHAT_RESPONSE", e);
        }
    }

    private void handleWorkflowSuggestResponse(AiResultDto result) {
        try {
            log.info("Handling WORKFLOW_SUGGEST_RESPONSE: {}", result);
            java.util.Map<String, Object> data = result.getResult();
            if (data == null) {
                log.error("Workflow suggest response has no data");
                return;
            }

            String xml = (String) data.get("xml");
            String correlationId = result.getCorrelationId();
            
            // Extract companyId from correlationId: "wf-suggest-{companyId}-{timestamp}"
            String[] parts = correlationId.split("-");
            if (parts.length >= 3) {
                String companyIdStr = parts[2];
                
                java.util.Map<String, Object> payload = new java.util.HashMap<>();
                payload.put("xml", xml);
                payload.put("correlationId", correlationId);
                
                messagingTemplate.convertAndSend("/topic/company/" + companyIdStr + "/workflow-suggest", (Object) payload);
                log.info("Sent workflow suggestion to company topic: {}", companyIdStr);
            }
        } catch (Exception e) {
            log.error("Failed to process WORKFLOW_SUGGEST_RESPONSE", e);
        }
    }

    private void handleWorkflowSuggestError(AiResultDto result) {
        try {
            log.info("Handling WORKFLOW_SUGGEST_ERROR for correlation_id={}", result.getCorrelationId());
            String[] parts = result.getCorrelationId().split("-");
            if (parts.length >= 3) {
                String companyIdStr = parts[2];
                
                java.util.Map<String, Object> payload = new java.util.HashMap<>();
                payload.put("error", result.getError() != null ? result.getError() : "AI Workflow Generation failed");
                payload.put("correlationId", result.getCorrelationId());
                
                messagingTemplate.convertAndSend("/topic/company/" + companyIdStr + "/workflow-suggest", (Object) payload);
            }
        } catch (Exception e) {
            log.error("Failed to process WORKFLOW_SUGGEST_ERROR", e);
        }
    }
}
