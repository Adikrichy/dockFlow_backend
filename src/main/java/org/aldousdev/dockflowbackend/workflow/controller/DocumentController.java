package org.aldousdev.dockflowbackend.workflow.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.aldousdev.dockflowbackend.workflow.dto.response.DocumentResponse;
import org.aldousdev.dockflowbackend.workflow.service.impl.DocumentServiceImpl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentServiceImpl documentService;

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
}