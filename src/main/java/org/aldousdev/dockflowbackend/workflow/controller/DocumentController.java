package org.aldousdev.dockflowbackend.workflow.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.auth.components.RequiresRoleLevel;
import org.aldousdev.dockflowbackend.auth.security.JWTService;
import org.aldousdev.dockflowbackend.auth.security.JwtAuthenticationToken;
import org.aldousdev.dockflowbackend.auth.service.impls.AuthServiceImpl;
import org.aldousdev.dockflowbackend.workflow.dto.response.DocumentResponse;
import org.aldousdev.dockflowbackend.workflow.dto.response.DocumentVersionResponse;
import org.aldousdev.dockflowbackend.workflow.entity.DocumentVersion;
import org.aldousdev.dockflowbackend.workflow.service.DocumentVersioningService;
import org.aldousdev.dockflowbackend.workflow.service.impl.DocumentServiceImpl;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Documents", description = "Управление документами и версиями")
public class DocumentController {

    private final DocumentServiceImpl documentService;
    private final DocumentVersioningService documentVersioningService;
    private final JWTService jwtService;
    private final AuthServiceImpl authService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresRoleLevel(10) // All workers and above
    @Operation(summary = "Upload a PDF document", description = "Uploads a PDF file to the system. Only company members can upload.")
    public ResponseEntity<DocumentResponse> uploadDocument(
            @Parameter(
                    description = "PDF file to upload",
                    required = true,
                    content = @Content(mediaType = "multipart/form-data")
            )
            @RequestParam("file") MultipartFile file) {

        DocumentResponse documentResponse = documentService.uploadDocument(file);
        return ResponseEntity.ok(documentResponse);
    }

    @GetMapping("/user")
    @RequiresRoleLevel(10) // All workers and above
    @Operation(summary = "Get documents for current user/company", 
            description = "Returns list of documents belonging to the user's company context")
    public ResponseEntity<List<DocumentResponse>> getUserDocuments(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            log.error("Invalid authentication token type");
            return ResponseEntity.status(401).build();
        }
        
        Long companyId = jwtService.extractCompanyId(jwtAuth.getToken());
        log.info("Fetching documents for company context: {}", companyId);
        
        List<DocumentResponse> documents = documentService.getCompanyDocuments(companyId);
        return ResponseEntity.ok(documents);
    }

    @GetMapping("/{documentId}")
    @RequiresRoleLevel(10)
    @Operation(summary = "Get document details", description = "Returns detailed information about a specific document")
    public ResponseEntity<DocumentResponse> getDocument(@PathVariable Long documentId) {
        log.info("Fetching details for document: {}", documentId);
        return ResponseEntity.ok(documentService.getDocument(documentId));
    }

    @GetMapping("/{documentId}/download")
    @RequiresRoleLevel(10)
    @Operation(summary = "Download document file", description = "Returns the actual PDF file content")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable Long documentId,
            @RequestParam(defaultValue = "false") boolean preview) {
        log.info("Downloading file for document: {}", documentId);
        Resource resource = documentService.downloadDocument(documentId);

        var document = documentService.getDocumentEntity(documentId);
        MediaType mediaType;
        try {
            mediaType = document.getContentType() != null
                    ? MediaType.parseMediaType(document.getContentType())
                    : MediaType.APPLICATION_OCTET_STREAM;
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        String filename = document.getOriginalFilename() != null
                ? document.getOriginalFilename()
                : (resource.getFilename() != null ? resource.getFilename() : "document");

        if (filename.toLowerCase().endsWith(".pdf")) {
            mediaType = MediaType.APPLICATION_PDF;
        }

        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok()
                .contentType(mediaType);

        if (!preview) {
            responseBuilder.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        }

        return responseBuilder.body(resource);
    }

    @GetMapping("/{documentId}/versions/{versionNumber}/download")
    @RequiresRoleLevel(10)
    @Operation(summary = "Download document version", description = "Returns the file content for a specific version")
    public ResponseEntity<Resource> downloadDocumentVersion(
            @PathVariable Long documentId,
            @PathVariable Integer versionNumber,
            @RequestParam(defaultValue = "false") boolean preview) {
        log.info("Downloading file for document: {} version: {}", documentId, versionNumber);
        
        Resource resource = documentService.downloadDocumentVersion(documentId, versionNumber);
        var version = documentVersioningService.getVersion(documentId, versionNumber)
                .orElseThrow(() -> new RuntimeException("Version found in service but not here? Should not happen due to service check"));

        MediaType mediaType;
        try {
            mediaType = version.getContentType() != null
                    ? MediaType.parseMediaType(version.getContentType())
                    : MediaType.APPLICATION_OCTET_STREAM;
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        String filename = version.getOriginalFilename() != null
                ? version.getOriginalFilename()
                : (resource.getFilename() != null ? resource.getFilename() : "document_v" + versionNumber);

        if (filename.toLowerCase().endsWith(".pdf")) {
            mediaType = MediaType.APPLICATION_PDF;
        }

        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok()
                .contentType(mediaType);

        if (!preview) {
            responseBuilder.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        }

        return responseBuilder.body(resource);
    }

    @PostMapping("/{documentId}/versions")
    @RequiresRoleLevel(10)
    @Operation(summary = "Create new version of document",
            description = "Creates a new version of an existing document with file changes")
    public ResponseEntity<DocumentVersionResponse> createDocumentVersion(
            @PathVariable Long documentId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "changeDescription", required = false) String changeDescription,
            @RequestParam(value = "changeType", defaultValue = "UPDATE") String changeType) {

        try {
            var document = documentService.getDocumentEntity(documentId);
            var currentUser = authService.getCurrentUser();
            var version = documentVersioningService.createNewVersion(document, file, currentUser, changeDescription, changeType);
            return ResponseEntity.ok(mapToVersionResponse(version));
        } catch (Exception e) {
            log.error("Failed to create document version", e);
            throw new RuntimeException("Error creating version: " + e.getMessage());
        }
    }

    @GetMapping("/{documentId}/versions")
    @RequiresRoleLevel(10)
    @Operation(summary = "Get all versions of document",
            description = "Returns list of all versions for a specific document")
    public ResponseEntity<List<DocumentVersionResponse>> getDocumentVersions(
            @PathVariable Long documentId) {

        log.info("Fetching versions for document: {}", documentId);
        List<DocumentVersionResponse> versions = documentVersioningService.getDocumentVersions(documentId).stream()
                .map(this::mapToVersionResponse)
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(versions);
    }

    @PostMapping("/{documentId}/watermark")
    @RequiresRoleLevel(10)
    @Operation(summary = "Add watermark to document",
            description = "Creates a new version of document with watermark")
    public ResponseEntity<DocumentVersionResponse> addWatermark(
            @PathVariable Long documentId,
            @RequestParam("watermarkText") String watermarkText) {

        try {
            var document = documentService.getDocumentEntity(documentId);
            var currentUser = authService.getCurrentUser();
            var version = documentVersioningService.createWatermarkedVersion(document, watermarkText, currentUser);
            return ResponseEntity.ok(mapToVersionResponse(version));
        } catch (Exception e) {
            log.error("Failed to add watermark", e);
            throw new RuntimeException("Error adding watermark: " + e.getMessage());
        }
    }

    @PostMapping("/{documentId}/sign")
    @RequiresRoleLevel(10)
    @Operation(summary = "Sign document",
            description = "Creates a signed version of the document")
    public ResponseEntity<DocumentVersionResponse> signDocument(
            @PathVariable Long documentId,
            @RequestParam("signatureText") String signatureText,
            @RequestParam("signerName") String signerName) {

        try {
            var document = documentService.getDocumentEntity(documentId);
            var currentUser = authService.getCurrentUser();
            var version = documentVersioningService.createSignedVersion(document, signatureText, signerName, currentUser);
            return ResponseEntity.ok(mapToVersionResponse(version));
        } catch (Exception e) {
            log.error("Failed to sign document", e);
            throw new RuntimeException("Error signing document: " + e.getMessage());
        }
    }

    @PostMapping("/{documentId}/versions/{versionNumber}/restore")
    @RequiresRoleLevel(10)
    @Operation(summary = "Restore document version",
            description = "Restores a specific version as the current version")
    public ResponseEntity<Void> restoreDocumentVersion(
            @PathVariable Long documentId,
            @PathVariable Integer versionNumber) {

        var currentUser = authService.getCurrentUser();
        documentVersioningService.restoreVersion(documentId, versionNumber, currentUser);
        return ResponseEntity.ok().build();
    }

    private DocumentVersionResponse mapToVersionResponse(DocumentVersion version) {
        return DocumentVersionResponse.builder()
                .id(version.getId())
                .documentId(version.getDocument().getId())
                .versionNumber(version.getVersionNumber())
                .filePath(version.getFilePath())
                .originalFilename(version.getOriginalFilename())
                .contentType(version.getContentType())
                .fileSize(version.getFileSize())
                .sha256Hash(version.getSha256Hash())
                .changeDescription(version.getChangeDescription())
                .changeType(version.getChangeType())
                .createdBy(version.getCreatedBy().getFirstName() + " " + version.getCreatedBy().getLastName())
                .createdAt(version.getCreatedAt())
                .workflowMetadata(version.getWorkflowMetadata())
                .isSigned(version.getIsSigned())
                .hasWatermark(version.getHasWatermark())
                .isCurrent(version.getIsCurrent())
                .build();
    }
}
