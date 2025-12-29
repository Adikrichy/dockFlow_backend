package org.aldousdev.dockflowbackend.workflow.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.aldousdev.dockflowbackend.workflow.dto.response.DocumentResponse;
import org.aldousdev.dockflowbackend.workflow.service.DocumentVersioningService;
import org.aldousdev.dockflowbackend.workflow.service.impl.DocumentServiceImpl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentServiceImpl documentService;
    private final DocumentVersioningService documentVersioningService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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

    @PostMapping("/{documentId}/versions")
    @Operation(summary = "Create new version of document",
            description = "Creates a new version of an existing document with file changes")
    public ResponseEntity<DocumentResponse> createDocumentVersion(
            @Parameter(description = "Document ID", required = true)
            @PathVariable Long documentId,
            @Parameter(description = "New file version")
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Description of changes")
            @RequestParam(value = "changeDescription", required = false) String changeDescription,
            @Parameter(description = "Type of change")
            @RequestParam(value = "changeType", defaultValue = "UPDATE") String changeType) {

        // TODO: Implement version creation endpoint
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{documentId}/versions")
    @Operation(summary = "Get all versions of document",
            description = "Returns list of all versions for a specific document")
    public ResponseEntity<List<?>> getDocumentVersions(
            @Parameter(description = "Document ID", required = true)
            @PathVariable Long documentId) {

        // TODO: Implement get versions endpoint
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{documentId}/watermark")
    @Operation(summary = "Add watermark to document",
            description = "Creates a new version of document with watermark")
    public ResponseEntity<DocumentResponse> addWatermark(
            @Parameter(description = "Document ID", required = true)
            @PathVariable Long documentId,
            @Parameter(description = "Watermark text")
            @RequestParam("watermarkText") String watermarkText) {

        // TODO: Implement watermark endpoint
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{documentId}/sign")
    @Operation(summary = "Sign document",
            description = "Creates a signed version of the document")
    public ResponseEntity<DocumentResponse> signDocument(
            @Parameter(description = "Document ID", required = true)
            @PathVariable Long documentId,
            @Parameter(description = "Signature text")
            @RequestParam("signatureText") String signatureText,
            @Parameter(description = "Signer name")
            @RequestParam("signerName") String signerName) {

        // TODO: Implement signing endpoint
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{documentId}/versions/{versionNumber}/restore")
    @Operation(summary = "Restore document version",
            description = "Restores a specific version as the current version")
    public ResponseEntity<Void> restoreDocumentVersion(
            @Parameter(description = "Document ID", required = true)
            @PathVariable Long documentId,
            @Parameter(description = "Version number to restore", required = true)
            @PathVariable Integer versionNumber) {

        // TODO: Implement version restore endpoint
        return ResponseEntity.ok().build();
    }
}