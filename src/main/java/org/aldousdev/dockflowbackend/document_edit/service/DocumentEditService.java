package org.aldousdev.dockflowbackend.document_edit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.auth.exceptions.ResourceNotFoundException;
import org.aldousdev.dockflowbackend.auth.service.impls.AuthServiceImpl;
import org.aldousdev.dockflowbackend.document_edit.dto.CommitEditSessionRequest;
import org.aldousdev.dockflowbackend.document_edit.dto.CommitEditSessionResponse;
import org.aldousdev.dockflowbackend.document_edit.dto.EditorConfigResponse;
import org.aldousdev.dockflowbackend.document_edit.dto.StartEditSessionResponse;
import org.aldousdev.dockflowbackend.document_edit.entity.DocumentEditSession;
import org.aldousdev.dockflowbackend.document_edit.enums.EditSessionStatus;
import org.aldousdev.dockflowbackend.document_edit.repository.DocumentEditSessionRepository;
import org.aldousdev.dockflowbackend.workflow.entity.Document;
import org.aldousdev.dockflowbackend.workflow.entity.DocumentVersion;
import org.aldousdev.dockflowbackend.workflow.repository.DocumentRepository;
import org.aldousdev.dockflowbackend.workflow.service.DocumentVersioningService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentEditService {

    private static final String DOCX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final DocumentRepository documentRepository;
    private final DocumentEditSessionRepository sessionRepository;
    private final AuthServiceImpl authService;
    private final DocumentVersioningService documentVersioningService;
    private final OnlyOfficeClient onlyOfficeClient;

    @Value("${file.upload.dir:./uploads}")
    private String uploadDir;

    @Value("${app.public.url:http://localhost:8080}")
    private String publicBaseUrl;

    @Value("${onlyoffice.jwt.secret}")
    private String jwtSecret;

    @Value("${onlyoffice.docs.url:http://localhost:8081}")
    private String onlyOfficeDocsUrl;

    @Value("${app.internal.url:http://host.docker.internal:8080}")
    private String internalBaseUrl;

    private final ObjectMapper objectMapper;
    private final jakarta.persistence.EntityManager entityManager;

    @Transactional
    public StartEditSessionResponse startSession(Long documentId) {
        User user = authService.getCurrentUser();
        log.info("Starting edit session for document {} by user {}", documentId, user.getEmail());

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

        if (document.getContentType() == null || !document.getContentType().equalsIgnoreCase(DOCX_CONTENT_TYPE)) {
            log.error("Cannot edit document {} - not DOCX (content type: {})", documentId, document.getContentType());
            throw new IllegalStateException("Only DOCX documents can be edited in editor");
        }

        // Find all active sessions for this document
        List<DocumentEditSession> existingSessions = sessionRepository.findByDocumentIdAndStatus(documentId, EditSessionStatus.ACTIVE);
        
        if (!existingSessions.isEmpty()) {
            // Force close ALL existing sessions to ensure a fresh start with OnlyOffice
            // This prevents "UpdateVersion expired" and stale key issues
            log.warn("Found {} active sessions for document {}, closing ALL of them to start fresh", existingSessions.size(), documentId);
            
            for (DocumentEditSession oldSession : existingSessions) {
                oldSession.setStatus(EditSessionStatus.COMMITTED); // Or DISCARDED if we want to be explicit
                sessionRepository.save(oldSession);
                log.info("Closed old session {} for document {}", oldSession.getSessionKey(), documentId);
            }
        }

        String sessionKey = UUID.randomUUID().toString().replace("-", "");
        String onlyofficeKey = UUID.randomUUID().toString();

        try {
            Path sessionsDir = Paths.get(uploadDir, "edit-sessions");
            Files.createDirectories(sessionsDir);

            String baseName = sanitizeFilename(document.getOriginalFilename());
            if (!baseName.toLowerCase().endsWith(".docx")) {
                baseName = baseName + ".docx";
            }

            Path workingPath = sessionsDir.resolve(sessionKey + "_" + baseName);
            Path sourcePath = Paths.get(document.getFilePath());
            
            if (!Files.exists(sourcePath)) {
                log.error("Source document file not found: {}", sourcePath);
                throw new RuntimeException("Source document file not found: " + sourcePath);
            }
            
            Files.copy(sourcePath, workingPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Copied document to working path: {}", workingPath);

            DocumentEditSession session = DocumentEditSession.builder()
                    .document(document)
                    .createdBy(user)
                    .sessionKey(sessionKey)
                    .onlyofficeKey(onlyofficeKey)
                    .workingDocxPath(workingPath.toString())
                    .status(EditSessionStatus.ACTIVE)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            sessionRepository.save(session);
            log.info("Created edit session {} for document {}", sessionKey, documentId);

            return StartEditSessionResponse.builder().sessionKey(sessionKey).build();
        } catch (Exception e) {
            log.error("Failed to start edit session for document {}", documentId, e);
            throw new RuntimeException("Failed to start edit session: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public EditorConfigResponse getEditorConfig(String sessionKey) {
        User user = authService.getCurrentUser();
        log.info("Getting editor config for session: {} by user: {}", sessionKey, user.getEmail());

        DocumentEditSession session = sessionRepository.findBySessionKey(sessionKey)
                .orElseThrow(() -> new ResourceNotFoundException("Edit session not found"));

        if (session.getStatus() != EditSessionStatus.ACTIVE) {
            throw new IllegalStateException("Edit session is not active");
        }

        String fileUrl = internalBaseUrl + "/api/document-edit/file/" + sessionKey;
        String callbackUrl = internalBaseUrl + "/api/document-edit/onlyoffice/callback/" + sessionKey;

        // Создаем конфиг для генерации токена
        Map<String, Object> doc = new HashMap<>();
        doc.put("fileType", "docx");
        doc.put("key", session.getOnlyofficeKey());
        doc.put("title", session.getDocument().getOriginalFilename());
        doc.put("url", fileUrl);

        Map<String,Object> fileAccessClaims = new HashMap<>();
        fileAccessClaims.put("sessionKey", sessionKey);
        String fileToken = generateToken(fileAccessClaims);
        doc.put("token", fileToken);

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", String.valueOf(user.getId()));
        userMap.put("name", user.getFirstName() + " " + user.getLastName());

        Map<String, Object> editorConfig = new HashMap<>();
        editorConfig.put("mode", "edit");
        editorConfig.put("callbackUrl", callbackUrl);
        editorConfig.put("user", userMap);

        Map<String, Object> customization = new HashMap<>();
        customization.put("forcesave", true);
        editorConfig.put("customization", customization);

        Map<String, Object> config = new HashMap<>();
        config.put("documentType", "word");
        config.put("document", doc);
        config.put("editorConfig", editorConfig);

        // Генерируем токен для конфигурации
        String configToken = generateToken(config);
        config.put("token", configToken);

        // Добавляем documentServerUrl ПОСЛЕ генерации токена
        config.put("documentServerUrl", onlyOfficeDocsUrl);

        log.info("Returning editor config for session {}", sessionKey);
        log.info("Generated editor config for session {}: {}", sessionKey, config); // <-- Добавьте это
        return EditorConfigResponse.builder().config(config).build();
    }

    @Transactional
    public void applyOnlyOfficeSave(String onlyofficeKey, Integer status, String downloadUrl) {
        DocumentEditSession session = sessionRepository.findByOnlyofficeKey(onlyofficeKey)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found for key"));

        if (session.getStatus() != EditSessionStatus.ACTIVE) {
            return;
        }

        // OnlyOffice callback status:
        // 2 - document is ready for saving, 6/7 - force save variants (depends on config/version).
        // We save only when URL is present and status indicates save.
        log.info("Received OnlyOffice callback for key {}. Status: {}. URL: {}", onlyofficeKey, status, downloadUrl);
        if (status == null || !(status == 2 || status == 6 || status == 7)) {
            return;
        }

        byte[] bytes = onlyOfficeClient.downloadFile(downloadUrl);
        
        // Validate DOCX structure (ZIP header: PK..)
        if (bytes.length < 4 || bytes[0] != 0x50 || bytes[1] != 0x4B) { // 'P' 'K'
            String contentPreview = new String(bytes, 0, Math.min(bytes.length, 200), StandardCharsets.UTF_8);
            log.error("Downloaded file from OnlyOffice is NOT a valid DOCX/ZIP. URL: {}. Content preview: {}", downloadUrl, contentPreview);
            throw new RuntimeException("Downloaded file is corrupted or not a DOCX. See logs for content preview.");
        }

        try {
            Files.write(Paths.get(session.getWorkingDocxPath()), bytes);
            session.setUpdatedAt(LocalDateTime.now());
            sessionRepository.save(session);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save edited file: " + e.getMessage(), e);
        }
    }

    @Transactional
    public CommitEditSessionResponse commit(String sessionKey, CommitEditSessionRequest request) {
        User user = authService.getCurrentUser();

        DocumentEditSession session = sessionRepository.findBySessionKey(sessionKey)
                .orElseThrow(() -> new ResourceNotFoundException("Edit session not found"));

        if (session.getStatus() != EditSessionStatus.ACTIVE) {
            throw new IllegalStateException("Edit session is not active");
        }

        // --- FORCE SAVE LOGIC START ---
        // Trigger force save to ensure OnlyOffice flushes changes to backend
        log.info("Triggering FORCE SAVE for session {}", sessionKey);
        LocalDateTime beforeSave = session.getUpdatedAt();
        
        onlyOfficeClient.executeCommand("forcesave", session.getOnlyofficeKey());

        // Wait for callback to update the session (max 15 seconds)
        try {
            for (int i = 0; i < 30; i++) {
                Thread.sleep(500); // 30 * 500ms = 15 seconds
                
                // Force refresh from DB to bypass first-level cache
                entityManager.refresh(session);
                
                if (session.getUpdatedAt().isAfter(beforeSave)) {
                    log.info("Session updated via callback at {}. Proceeding to commit.", session.getUpdatedAt());
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // --- FORCE SAVE LOGIC END ---

        Path docxPath = Paths.get(session.getWorkingDocxPath());
        if (!Files.exists(docxPath)) {
            throw new IllegalStateException("Working DOCX not found");
        }

        String changeDescription = request != null ? request.getChangeDescription() : null;
        if (changeDescription == null || changeDescription.isBlank()) {
            changeDescription = "Edited in editor";
        }

        Document document = session.getDocument();

        DocumentVersion docxVersion;
        try {
            docxVersion = documentVersioningService.createNewVersionFromFile(
                    document,
                    docxPath,
                    document.getOriginalFilename(),
                    DOCX_CONTENT_TYPE,
                    user,
                    changeDescription,
                    "EDIT"
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to create DOCX version: " + e.getMessage(), e);
        }

        session.setStatus(EditSessionStatus.COMMITTED);
        session.setCommittedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);

        return CommitEditSessionResponse.builder()
                .docxVersionId(docxVersion.getId())
                .pdfVersionId(null)
                .build();
    }

    private String sanitizeFilename(String name) {
        if (name == null) return "document.docx";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String replaceExt(String filename, String ext) {
        if (filename == null) return "document" + ext;
        int idx = filename.lastIndexOf('.');
        if (idx <= 0) return filename + ext;
        return filename.substring(0, idx) + ext;
    }

    // В DocumentEditService.java замените метод generateToken:

    private String generateToken(Map<String, Object> payload) {
        try {
            SecretKey secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

            // НЕ оборачивайте в "payload"! Добавляйте поля напрямую
            JwtBuilder builder = Jwts.builder();

            for (Map.Entry<String, Object> entry : payload.entrySet()) {
                builder.claim(entry.getKey(), entry.getValue());
            }

            // Set expiration to 24 hours to avoid "UpdateVersion expired" for long sessions
            builder.setExpiration(new java.util.Date(System.currentTimeMillis() + 86400000)); // 24 hours

            return builder.signWith(secretKey).compact();
        } catch (Exception e) {
            log.error("Failed to generate token: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate token", e);
        }
    }
}
