package org.aldousdev.dockflowbackend.workflow.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

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
    private List<Integer> allowedRoleLevels;

    private String createdByName;
    private boolean isActive;
    private boolean canStart;
}
