package org.aldousdev.dockflowbackend.ai.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.ai.config.AiRabbitConfig;
import org.aldousdev.dockflowbackend.ai.dto.AiResultDto;
import org.aldousdev.dockflowbackend.ai.repository.DocumentAiAnalysisRepository;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiResultListener {

    private final DocumentAiAnalysisRepository repo;

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
                if (summaryObj instanceof java.util.List) {
                    entity.setSummary(String.join("; ", (java.util.List<String>) summaryObj));
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
                log.error("AI analysis error for correlation_id={}: {}", result.getCorrelationId(), result.getError());
                entity.setStatus("ERROR");
                entity.setError(result.getError());
            }

            repo.save(entity);
        }, () -> {
            log.warn("No DocumentAiAnalysis row found for correlation_id={}", result.getCorrelationId());
        });
    }
}
