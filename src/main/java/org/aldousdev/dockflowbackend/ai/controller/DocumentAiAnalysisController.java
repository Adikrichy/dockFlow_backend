package org.aldousdev.dockflowbackend.ai.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.ai.dto.AiAnalysisResponse;
import org.aldousdev.dockflowbackend.ai.service.DocumentAiAnalysisService;
import org.springframework.http.ResponseEntity;
import org.aldousdev.dockflowbackend.chat.dto.ChatMessageDTO;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/document")
@RequiredArgsConstructor
@Slf4j
public class DocumentAiAnalysisController {

    private final DocumentAiAnalysisService aiAnalysisService;

    @PostMapping("/{documentId}/versions/{versionId}/analyze")
    public ResponseEntity<AiAnalysisResponse> startAnalysis(
            @PathVariable Long documentId,
            @PathVariable Long versionId,
            @RequestParam(required = false) String provider) {

        log.info("Received AI analysis request for document {} version {} (provider: {})", 
            documentId, versionId, provider);

        AiAnalysisResponse response = aiAnalysisService.startDocumentAnalysis(documentId, versionId, provider);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{documentId}/versions/{versionId}/review")
    public ResponseEntity<AiAnalysisResponse> startReview(
            @PathVariable Long documentId,
            @PathVariable Long versionId,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String topic) {

        log.info("Received AI review request for document {} version {} (topic: {})", 
            documentId, versionId, topic);

        AiAnalysisResponse response = aiAnalysisService.startDocumentReview(documentId, versionId, provider, topic);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{documentId}/versions/{versionId}/analysis")
    public ResponseEntity<AiAnalysisResponse> getAnalysis(
            @PathVariable Long documentId,
            @PathVariable Long versionId) {

        AiAnalysisResponse response = aiAnalysisService.getAnalysisResult(documentId, versionId);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{documentId}/versions/{versionId}/chat")
    public ResponseEntity<ChatMessageDTO> startChat(
            @PathVariable Long documentId,
            @PathVariable Long versionId,
            @RequestBody String content) {
        
        log.info("Received AI document chat request for {} version {}", documentId, versionId);
        ChatMessageDTO response = aiAnalysisService.sendDocumentChatMessage(documentId, versionId, content);
        return ResponseEntity.ok(response);
    }
}