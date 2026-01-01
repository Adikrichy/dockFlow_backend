package org.aldousdev.dockflowbackend.workflow.service;

import org.aldousdev.dockflowbackend.workflow.dto.response.DocumentResponse;
import org.aldousdev.dockflowbackend.workflow.entity.Document;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {
    DocumentResponse uploadDocument(MultipartFile file);
    DocumentResponse getDocument(Long id);
    Document getDocumentEntity(Long id);
    List<DocumentResponse> getCompanyDocuments(Long companyId);
    Resource downloadDocument(Long id);
}
