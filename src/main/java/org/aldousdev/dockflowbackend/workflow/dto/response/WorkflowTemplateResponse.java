package org.aldousdev.dockflowbackend.workflow.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class WorkflowTemplateResponse {
    private Long id;
    private String name;
    private String description;
    private String stepsXml;
    private Long companyId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
