package org.aldousdev.dockflowbackend.ai.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.auth.exceptions.ResourceNotFoundException;
import org.aldousdev.dockflowbackend.workflow.entity.Document;
import org.aldousdev.dockflowbackend.workflow.entity.DocumentVersion;
import org.aldousdev.dockflowbackend.workflow.repository.DocumentRepository;
import org.aldousdev.dockflowbackend.workflow.repository.DocumentVersionRepository;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Internal API", description = "Service-to-Service internal endpoints (requires service JWT)")
public class InternalDocumentController {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;

    @GetMapping("/documents/{documentId}/versions/{versionId}/download")
    @Operation(
            summary = "Download document version by version ID",
            description = "Internal endpoint for AI service to download document files. Uses version ID (not version number)."
    )
    public ResponseEntity<Resource> downloadDocumentVersionById(
            @PathVariable Long documentId,
            @PathVariable Long versionId,
            @RequestParam(required = false) Long companyId) {

        log.info("Internal download request: documentId={}, versionId={}, companyId={}", documentId, versionId, companyId);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> {
                    log.warn("Document not found: {}", documentId);
                    return new ResourceNotFoundException("Document not found: " + documentId);
                });

        if (companyId != null && !document.getCompany().getId().equals(companyId)) {
            log.warn("Company mismatch: document belongs to company {} but request specified {}",
                    document.getCompany().getId(), companyId);
            throw new ResourceNotFoundException("Document not found for specified company");
        }

        DocumentVersion version = documentVersionRepository.findByIdAndDocumentId(versionId, documentId)
                .orElseThrow(() -> {
                    log.warn("Version not found: versionId={} for documentId={}", versionId, documentId);
                    return new ResourceNotFoundException("Version not found: " + versionId);
                });

        try {
            Path filePath = Paths.get(version.getFilePath());
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                log.error("File not accessible: {}", version.getFilePath());
                throw new RuntimeException("File not accessible: " + version.getFilePath());
            }

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
                    : "document_v" + version.getVersionNumber();

            log.info("Serving file: {} ({} bytes) for version {}", filename, version.getFileSize(), versionId);

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(resource);

        } catch (MalformedURLException e) {
            log.error("Invalid file path: {}", version.getFilePath(), e);
            throw new RuntimeException("Invalid file path: " + e.getMessage());
        }
    }

    @GetMapping("/documents/{documentId}/download")
    @Operation(
            summary = "Download current document version",
            description = "Internal endpoint for AI service to download the main document file."
    )
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable Long documentId,
            @RequestParam(required = false) Long companyId) {

        log.info("Internal download request for main document: documentId={}, companyId={}", documentId, companyId);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> {
                    log.warn("Document not found: {}", documentId);
                    return new ResourceNotFoundException("Document not found: " + documentId);
                });

        if (companyId != null && !document.getCompany().getId().equals(companyId)) {
            log.warn("Company mismatch: document belongs to company {} but request specified {}",
                    document.getCompany().getId(), companyId);
            throw new ResourceNotFoundException("Document not found for specified company");
        }

        try {
            Path filePath = Paths.get(document.getFilePath());
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                log.error("File not accessible: {}", document.getFilePath());
                throw new RuntimeException("File not accessible: " + document.getFilePath());
            }

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
                    : "document";

            log.info("Serving file: {} ({} bytes)", filename, document.getFileSize());

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(resource);

        } catch (MalformedURLException e) {
            log.error("Invalid file path: {}", document.getFilePath(), e);
            throw new RuntimeException("Invalid file path: " + e.getMessage());
        }
    }
}
