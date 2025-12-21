package org.aldousdev.dockflowbackend.workflow.service;

import org.aldousdev.dockflowbackend.workflow.dto.response.DocumentResponse;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentService {
    DocumentResponse uploadDocument(MultipartFile file);
}
