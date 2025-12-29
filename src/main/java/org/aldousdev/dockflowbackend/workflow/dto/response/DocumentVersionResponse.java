package org.aldousdev.dockflowbackend.workflow.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DocumentVersionResponse {
    private Long id;
    private Long documentId;
    private Integer versionNumber;
    private String filePath;
    private String originalFilename;
    private String contentType;
    private Long fileSize;
    private String sha256Hash;
    private String changeDescription;
    private String changeType;
    private String createdBy;
    private LocalDateTime createdAt;
    private String workflowMetadata;
    private Boolean isSigned;
    private Boolean hasWatermark;
    private Boolean isCurrent;
}
