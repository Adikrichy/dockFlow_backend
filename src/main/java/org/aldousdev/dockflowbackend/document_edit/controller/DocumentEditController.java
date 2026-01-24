package org.aldousdev.dockflowbackend.document_edit.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.document_edit.dto.*;
import org.aldousdev.dockflowbackend.document_edit.entity.DocumentEditSession;
import org.aldousdev.dockflowbackend.document_edit.repository.DocumentEditSessionRepository;
import org.aldousdev.dockflowbackend.document_edit.service.DocumentEditService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/document-edit")
@RequiredArgsConstructor
public class DocumentEditController {

    private final DocumentEditService documentEditService;
    private final DocumentEditSessionRepository sessionRepository;

    @PostMapping("/session/start")
    public ResponseEntity<StartEditSessionResponse> start(@RequestBody StartEditSessionRequest request) {
        return ResponseEntity.ok(documentEditService.startSession(request.getDocumentId(), request.getVersionNumber()));
    }

    @GetMapping("/session/{sessionKey}/config")
    public ResponseEntity<EditorConfigResponse> config(@PathVariable String sessionKey) {
        return ResponseEntity.ok(documentEditService.getEditorConfig(sessionKey));
    }

    @GetMapping("/file/{sessionKey}")
    public ResponseEntity<Resource> file(@PathVariable String sessionKey) {
        log.info("File download requested for session: {}", sessionKey);

        try {
            DocumentEditSession session = sessionRepository.findBySessionKey(sessionKey)
                    .orElseThrow(() -> {
                        log.error("Session not found: {}", sessionKey);
                        return new RuntimeException("Session not found: " + sessionKey);
                    });

            Path path = Paths.get(session.getWorkingDocxPath());
            log.info("Attempting to read file: {}", path);

            if (!Files.exists(path)) {
                log.error("File not found: {}", path);
                throw new RuntimeException("File not found: " + path);
            }

            if (!Files.isReadable(path)) {
                log.error("File not readable: {}", path);
                throw new RuntimeException("File not readable: " + path);
            }

            byte[] bytes = Files.readAllBytes(path);
            log.info("Successfully read file {} ({} bytes)", path, bytes.length);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .body(new ByteArrayResource(bytes));
        } catch (Exception e) {
            log.error("Failed to download file for session {}: {}", sessionKey, e.getMessage(), e);
            throw new RuntimeException("Failed to download file: " + e.getMessage(), e);
        }
    }

    @PostMapping("/onlyoffice/callback/{sessionKey}")
    public ResponseEntity<Map<String, Object>> callback(
            @PathVariable String sessionKey,
            @RequestBody OnlyOfficeCallbackRequest request
    ) {
        log.info("Callback received for session: {}", sessionKey);

        // Теперь проверку токена делает OnlyOfficeJwtFilter
        // Мы можем просто обработать callback

        if (request.getUrl() != null && request.getKey() != null) {
            documentEditService.applyOnlyOfficeSave(request.getKey(), request.getStatus(), request.getUrl());
        }

        return ResponseEntity.ok(Map.of("error", 0));
    }

    @PostMapping("/session/{sessionKey}/commit")
    public ResponseEntity<CommitEditSessionResponse> commit(
            @PathVariable String sessionKey,
            @RequestBody(required = false) CommitEditSessionRequest request
    ) {
        return ResponseEntity.ok(documentEditService.commit(sessionKey, request));
    }
}