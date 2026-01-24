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
import org.aldousdev.dockflowbackend.ai.service.AiFacade;
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
import java.util.*;

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
    private final AiFacade aiFacade;

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
    public synchronized StartEditSessionResponse startSession(Long documentId, Integer versionNumber) {
        User user = authService.getCurrentUser();
        log.info("Starting edit session for document {} (version: {}) by user {}", 
                documentId, versionNumber != null ? versionNumber : "current", user.getEmail());

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

        if (document.getContentType() == null || !document.getContentType().equalsIgnoreCase(DOCX_CONTENT_TYPE)) {
            log.error("Cannot edit document {} - not DOCX (content type: {})", documentId, document.getContentType());
            throw new IllegalStateException("Only DOCX documents can be edited in editor");
        }

        // Find source file path: specific version or latest
        String sourceFilePath;
        Integer baseVersionToUse;

        if (versionNumber != null) {
            DocumentVersion version = documentVersioningService.getVersion(documentId, versionNumber)
                    .orElseThrow(() -> new ResourceNotFoundException("Version " + versionNumber + " not found"));
            sourceFilePath = version.getFilePath();
            baseVersionToUse = versionNumber;
        } else {
            // Default to current version
            DocumentVersion currentVersion = documentVersioningService.getCurrentVersion(documentId)
                    .orElseThrow(() -> new IllegalStateException("No current version found for document"));
            sourceFilePath = currentVersion.getFilePath();
            baseVersionToUse = currentVersion.getVersionNumber();
        }

        // Find existing active session for this specific document, version, AND user (no co-editing)
        List<DocumentEditSession> existingSessions = sessionRepository.findByDocumentIdAndBaseVersionNumberAndUserIdAndStatus(
                documentId, baseVersionToUse, user.getId(), EditSessionStatus.ACTIVE);
        
        if (!existingSessions.isEmpty()) {
            // Reuse existing session for THIS user only
            DocumentEditSession existingSession = existingSessions.get(0);
            log.info("Found active session {} for document {} V{} by user {}. Reusing.", 
                    existingSession.getSessionKey(), documentId, baseVersionToUse, user.getEmail());
            return StartEditSessionResponse.builder().sessionKey(existingSession.getSessionKey()).build();
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
            Path sourcePath = Paths.get(sourceFilePath);
            
            if (!Files.exists(sourcePath)) {
                log.error("Source document file not found: {}", sourcePath);
                throw new RuntimeException("Source document file not found: " + sourcePath);
            }
            
            Files.copy(sourcePath, workingPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Copied document (V{}) to working path: {}", baseVersionToUse, workingPath);

            DocumentEditSession session = DocumentEditSession.builder()
                    .document(document)
                    .createdBy(user)
                    .sessionKey(sessionKey)
                    .onlyofficeKey(onlyofficeKey)
                    .workingDocxPath(workingPath.toString())
                    .status(EditSessionStatus.ACTIVE)
                    .baseVersionNumber(baseVersionToUse)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            sessionRepository.save(session);
            log.info("Created edit session {} for document {} base version {}", sessionKey, documentId, baseVersionToUse);

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
        
        // Fast co-editing mode ensures changes are synced to the Document Server immediately
        // This is required for 'forcesave' command to work correctly.
        Map<String, Object> coEditing = new HashMap<>();
        coEditing.put("mode", "fast");
        editorConfig.put("coEditing", coEditing);

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
        log.info("Generated editor config for session {}: {}", sessionKey, config);

//        Map<String, Object> config = generateEditorConfig(session, user);
//        log.info("Returning editor config for session {}", sessionKey);
//        log.debug("Generated editor config: {}", config);
        return EditorConfigResponse.builder().config(config).build();
    }

    @Transactional
    public void applyOnlyOfficeSave(String onlyofficeKey, Integer status, String downloadUrl) {
        DocumentEditSession session = sessionRepository.findByOnlyofficeKey(onlyofficeKey)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found for key"));

        if (session.getStatus() != EditSessionStatus.ACTIVE) {
            log.warn("Received callback for non-active session {} (status: {}). Ignoring.", onlyofficeKey, session.getStatus());
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
            log.error("Downloaded file from OnlyOffice is NOT a valid DOCX/ZIP for session {}. URL: {}. Content preview: {}", 
                    onlyofficeKey, downloadUrl, contentPreview);
            throw new RuntimeException("Downloaded file is corrupted or not a DOCX. See logs for content preview.");
        }

        try {
            Files.write(Paths.get(session.getWorkingDocxPath()), bytes);
            session.setUpdatedAt(LocalDateTime.now());
            sessionRepository.save(session);
            log.info("Successfully updated working file for session {} from OnlyOffice callback", onlyofficeKey);
        } catch (Exception e) {
            log.error("Failed to write edited file for session {}: {}", onlyofficeKey, e.getMessage(), e);
            throw new RuntimeException("Failed to save edited file: " + e.getMessage(), e);
        }
    }

    @Transactional
    public CommitEditSessionResponse commit(String sessionKey, CommitEditSessionRequest request) {
        User user = authService.getCurrentUser();

        DocumentEditSession session = sessionRepository.findBySessionKey(sessionKey)
                .orElseThrow(() -> new ResourceNotFoundException("Edit session not found"));

        if (session.getStatus() == EditSessionStatus.COMMITTED) {
            log.info("Session {} already committed. Returning existing info.", sessionKey);
            // Try to find the latest version created by this user for this document recently
            DocumentVersion lastVersion = documentVersioningService.getDocumentVersions(session.getDocument().getId())
                    .stream()
                    .filter(v -> v.getCreatedBy().getId().equals(user.getId()))
                    .findFirst()
                    .orElse(null);

            return CommitEditSessionResponse.builder()
                    .docxVersionId(lastVersion != null ? lastVersion.getId() : null)
                    .pdfVersionId(null)
                    .build();
        }

        if (session.getStatus() != EditSessionStatus.ACTIVE) {
            throw new IllegalStateException("Edit session is not active");
        }

        // --- FORCE SAVE LOGIC START ---
        // Trigger force save to ensure OnlyOffice flushes changes to backend
        log.info("Triggering FORCE SAVE for session {}", sessionKey);
        LocalDateTime beforeSave = session.getUpdatedAt();
        
        int forceSaveResult = onlyOfficeClient.executeCommand("forcesave", session.getOnlyofficeKey());

        boolean saved = false;
        if (forceSaveResult == 4) {
            log.info("OnlyOffice returned error 4 (no changes) for session {}. Procceding with current content.", sessionKey);
            saved = true; // Proceed with whatever content is in the working file
        } else {
            // Wait for callback to update the session (max 15 seconds)
            try {
                for (int i = 0; i < 30; i++) {
                    Thread.sleep(500); // 30 * 500ms = 15 seconds
                    
                    // Force refresh from DB to bypass first-level cache
                    entityManager.refresh(session);
                    
                    if (session.getUpdatedAt().isAfter(beforeSave)) {
                        log.info("Session updated via OnlyOffice callback at {}. Proceeding to commit.", session.getUpdatedAt());
                        saved = true;
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (!saved) {
            log.error("FORCE SAVE failed for session {}. OnlyOffice did not send callback in time.", sessionKey);
            throw new IllegalStateException("Failed to save changes: OnlyOffice editor did not respond to save request. Please try again in 5 seconds.");
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

        // Request AI analysis for the SPECIFIC version
        try {
            aiFacade.requestDocumentAnalyze(document.getId(), document.getCompany().getId());
            // TODO: Ideally AiFacade should support versionId, but for now we follow the existing pattern
            // and maybe update AiFacade later if needed.
        } catch (Exception e) {
            log.error("Failed to request AI analysis for committed version", e);
        }

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
            builder.expiration(new java.util.Date(System.currentTimeMillis() + 86400000)); // 24 hours

            return builder.signWith(secretKey).compact();
        } catch (Exception e) {
            log.error("Failed to generate token: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate token", e);
        }
    }

//    private Map<String,Object> generateEditorConfig(DocumentEditSession session, User user) {
//        Map<String,Object> config = new HashMap<>();
//
//        Map<String, Object> document = new HashMap<>();
//        document.put("title", session.getDocument().getOriginalFilename());
//        document.put("fileType", "docx");
//        document.put("key", session.getOnlyofficeKey());
//        document.put("url", internalBaseUrl + "/api/document-edit/file/" + session.getSessionKey());
//
//        Map<String, Object> permissions = new HashMap<>();
//        permissions.put("edit", true);
//        permissions.put("download", true);
//        permissions.put("print", true);
//        permissions.put("review", true);
//        permissions.put("comment", true);
//        permissions.put("fillForms", true);
//        permissions.put("modifyFilter", true);
//        permissions.put("modifyContentControl", true);
//        document.put("permissions", permissions);
//
//        Map<String, Object> fileAccessClaims = new HashMap<>();
//        fileAccessClaims.put("sessionKey", session.getSessionKey());
//        String fileToken = generateToken(fileAccessClaims);
//        document.put("token", fileToken);
//
//        config.put("document", document);
//        config.put("documentType", "word");
//
//        Map<String, Object> editorConfig = new HashMap<>();
//        editorConfig.put("mode", "edit");
//        editorConfig.put("lang", "ru");
//        editorConfig.put("callbackUrl", internalBaseUrl + "/api/document-edit/onlyoffice/callback/" + session.getSessionKey());
//
//        Map<String, Object> userInfo = new HashMap<>();
//        userInfo.put("id", String.valueOf(user.getId()));
//        userInfo.put("name", user.getFirstName() + " " + user.getLastName());
//        editorConfig.put("user", userInfo);
//
//        Map<String, Object> customization = new HashMap<>();
//
//        Map<String, Object> customer = new HashMap<>();
//        customer.put("name", "DockFlow");
//        customer.put("address", "Manasa 34");
//        customer.put("mail", "adil.erzhanoc.70@gmail.com");
//        customer.put("www", "dockflow.com");
//        customer.put("info", "System of automatization of Workflow and Document ");
//        customer.put("logo", publicBaseUrl + "/images/logo.png");
//        customer.put("logoUrl", publicBaseUrl);
//        customization.put("customer", customer);
//
//        Map<String, Object> logo = new HashMap<>();
//        logo.put("image", publicBaseUrl + "/images/dockflow-editor-logo.svg");
//        logo.put("imageEmbedded", publicBaseUrl + "/images/dockflow-icon.svg");
//        logo.put("imageDark", publicBaseUrl + "/images/dockflow-logo-dark.svg");
//        logo.put("url", publicBaseUrl + "/documents");
//        logo.put("visible" , true);
//        customization.put("logo", logo);
//
//        customization.put("about", true);
//        customization.put("feedback", false);
//        customization.put("help", false);
//
//        customization.put("forcesave", true);
//        customization.put("autosave", true);
//
//        Map<String, Object> goback = new HashMap<>();
//        goback.put("blank", false);
//        goback.put("text", "go back to documents");
//        goback.put("url", publicBaseUrl + "/documents");
//        customization.put("goback", goback);
//
//        customization.put("compactHeader", false);
//        customization.put("compactToolbar", false);
//        customization.put("hideRightMenu", false);
//        customization.put("hideRulers", false);
//        customization.put("unit", "cm");
//
//        Map<String, Object> features = new HashMap<>();
//        features.put("spellcheck", true);
//        features.put("roles", true);
//        customization.put("features", features);
//
//        List<String> styles = List.of(publicBaseUrl + "/css/onlyoffice-custom.css");
//        customization.put("styles", styles);
//
//        customization.put("comments", true);
//        customization.put("mentionShare", true);
//
//        Map<String, Object> review = new HashMap<>();
//        review.put("showReviewChanges", true);
//        review.put("reviewDisplay", "markup");
//        review.put("trackChanges", true);
//        customization.put("review", review);
//
//        editorConfig.put("customization", customization);
//        config.put("editorConfig", editorConfig);
//
//        String configToken = generateToken(config);
//        config.put("token", configToken);
//        config.put("documentServerUrl", onlyOfficeDocsUrl);
//        return config;
//    }


}
