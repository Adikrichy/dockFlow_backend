package org.aldousdev.dockflowbackend.workflow.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class DocumentResponse {
    private Long id;
    private String originalFilename;
    private String contentType;
    private String filePath;
    private Long fileSize;
    private LocalDateTime uploadedAt;
    private String uploadedBy;
    private boolean signed;
}
