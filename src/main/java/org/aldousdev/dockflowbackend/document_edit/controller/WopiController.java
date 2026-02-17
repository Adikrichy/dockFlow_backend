package org.aldousdev.dockflowbackend.document_edit.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.auth.repository.UserRepository;
import org.aldousdev.dockflowbackend.document_edit.entity.DocumentEditSession;
import org.aldousdev.dockflowbackend.document_edit.entity.WopiToken;
import org.aldousdev.dockflowbackend.document_edit.repository.DocumentEditSessionRepository;
import org.aldousdev.dockflowbackend.document_edit.service.WopiTokenService;
import org.aldousdev.dockflowbackend.workflow.service.DocumentHashService;
import org.aldousdev.dockflowbackend.workflow.service.DocumentVersioningService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/wopi")
@RequiredArgsConstructor
public class WopiController {

    private final WopiTokenService tokenService;
    private final DocumentEditSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final DocumentVersioningService documentVersioningService;
    private final DocumentHashService documentHashService;

    @GetMapping("/files/{fileId}")
    public ResponseEntity<?> checkFileInfo(
            @PathVariable String fileId,
            @RequestParam("access_token") String token) {
        
        log.info("WOPI CheckFileInfo called for fileId: {} with token: {}", fileId, token);
        
        WopiToken wopiToken = validateToken(token);
        if (wopiToken == null) {
            log.error("WOPI CheckFileInfo: Token validation FAILED for token: {}", token);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        log.info("WOPI CheckFileInfo: Token validated successfully for session: {}", wopiToken.getSessionKey());

        DocumentEditSession session = sessionRepository.findBySessionKey(fileId)
                .orElse(null);
        if (session == null) {
            log.error("WOPI CheckFileInfo: Session NOT FOUND for fileId: {}", fileId);
            return ResponseEntity.notFound().build();
        }
        log.info("WOPI CheckFileInfo: Session found for fileId: {}", fileId);

        User user = userRepository.findById(wopiToken.getUserId()).orElse(null);
        if (user == null) {
            log.error("WOPI CheckFileInfo: User NOT FOUND for userId: {}", wopiToken.getUserId());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Get actual file size from disk (not from DB entity which may be null/stale)
        long actualFileSize = 0;
        try {
            Path filePath = Paths.get(session.getWorkingDocxPath());
            if (Files.exists(filePath)) {
                actualFileSize = Files.size(filePath);
            }
        } catch (Exception e) {
            log.warn("Could not determine file size for session {}: {}", fileId, e.getMessage());
        }

        Map<String, Object> info = new HashMap<>();
        info.put("BaseFileName", session.getDocument().getOriginalFilename());
        info.put("Size", actualFileSize);
        info.put("OwnerId", session.getDocument().getUploadedBy().getEmail());
        info.put("UserId", user.getEmail());
        info.put("UserFriendlyName", user.getFirstName() + " " + user.getLastName());
        info.put("UserCanWrite", true);
        info.put("ReadOnly", false);
        info.put("UserCanNotWriteRelative", true); // Disable "Save As"
        info.put("UserCanRename", false);
        info.put("SupportsLocks", true);
        info.put("SupportsGetLock", true);
        info.put("SupportsUpdate", true);
        
        // Essential for iframe communication
        info.put("PostMessageOrigin", "http://localhost:5173");

        log.info("WOPI CheckFileInfo: Returning OK for fileId: {} user: {} size: {} UserCanWrite: true", fileId, user.getEmail(), actualFileSize);
        return ResponseEntity.ok(info);
    }

    @GetMapping("/files/{fileId}/contents")
    public ResponseEntity<Resource> getFile(
            @PathVariable String fileId,
            @RequestParam("access_token") String token) {
        
        WopiToken wopiToken = validateToken(token);
        if (wopiToken == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        DocumentEditSession session = sessionRepository.findBySessionKey(fileId)
                .orElse(null);
        if (session == null) return ResponseEntity.notFound().build();

        try {
            Path path = Paths.get(session.getWorkingDocxPath());
            byte[] bytes = Files.readAllBytes(path);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(new ByteArrayResource(bytes));
        } catch (Exception e) {
            log.error("Failed to read file for WOPI GetFile: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/files/{fileId}/contents")
    public ResponseEntity<?> putFile(
            @PathVariable String fileId,
            @RequestParam("access_token") String token,
            @RequestBody byte[] content,
            @RequestHeader(value = "X-WOPI-Lock", required = false) String lockField) {
        
        WopiToken wopiToken = validateToken(token);
        if (wopiToken == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        DocumentEditSession session = sessionRepository.findBySessionKey(fileId)
                .orElse(null);
        if (session == null) return ResponseEntity.notFound().build();

        // Check lock
        if (session.getWopiLockValue() != null && !session.getWopiLockValue().equals(lockField)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .header("X-WOPI-Lock", session.getWopiLockValue())
                    .header("X-WOPI-LockFailureReason", "Lock mismatch")
                    .build();
        }

        try {
            // Idempotency check: Calculate hash
            String newHash = documentHashService.calculateSha256Hash(content);
            Path path = Paths.get(session.getWorkingDocxPath());
            byte[] currentBytes = Files.readAllBytes(path);
            String currentHash = documentHashService.calculateSha256Hash(currentBytes);

            if (newHash.equals(currentHash)) {
                log.info("WOPI PutFile: Content unchanged for session {}, skipping save.", fileId);
                return ResponseEntity.ok().build();
            }

            Files.write(path, content);
            session.setUpdatedAt(LocalDateTime.now());
            sessionRepository.save(session);
            
            log.info("WOPI PutFile: Successfully saved changes for session {}", fileId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to save WOPI PutFile: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/files/{fileId}")
    public ResponseEntity<?> handleLock(
            @PathVariable String fileId,
            @RequestParam("access_token") String token,
            @RequestHeader("X-WOPI-Override") String override,
            @RequestHeader(value = "X-WOPI-Lock", required = false) String lock,
            @RequestHeader(value = "X-WOPI-OldLock", required = false) String oldLock) {

        WopiToken wopiToken = validateToken(token);
        if (wopiToken == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        DocumentEditSession session = sessionRepository.findBySessionKey(fileId)
                .orElse(null);
        if (session == null) return ResponseEntity.notFound().build();

        String currentLock = session.getWopiLockValue();

        switch (override) {
            case "LOCK":
                if (currentLock == null) {
                    // Success lock
                    updateLock(session, lock);
                    return ResponseEntity.ok().build();
                } else if (currentLock.equals(lock)) {
                    // Already locked by this value, refresh it
                    updateLock(session, lock);
                    return ResponseEntity.ok().build();
                } else {
                    // Conflict
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .header("X-WOPI-Lock", currentLock)
                            .build();
                }

            case "UNLOCK":
                if (currentLock == null) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).header("X-WOPI-Lock", "").build();
                } else if (currentLock.equals(lock)) {
                    updateLock(session, null);
                    return ResponseEntity.ok().build();
                } else {
                    return ResponseEntity.status(HttpStatus.CONFLICT).header("X-WOPI-Lock", currentLock).build();
                }

            case "REFRESH_LOCK":
                if (currentLock == null) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).header("X-WOPI-Lock", "").build();
                } else if (currentLock.equals(lock)) {
                    updateLock(session, lock);
                    return ResponseEntity.ok().build();
                } else {
                    return ResponseEntity.status(HttpStatus.CONFLICT).header("X-WOPI-Lock", currentLock).build();
                }

            case "GET_LOCK":
                return ResponseEntity.ok()
                        .header("X-WOPI-Lock", currentLock != null ? currentLock : "")
                        .build();

            default:
                return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        }
    }

    private WopiToken validateToken(String token) {
        return tokenService.validateToken(token).orElse(null);
    }

    private void updateLock(DocumentEditSession session, String lock) {
        session.setWopiLockValue(lock);
        session.setWopiLockExpiresAt(lock == null ? null : LocalDateTime.now().plusMinutes(30));
        sessionRepository.save(session);
    }
}
