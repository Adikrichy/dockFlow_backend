package org.aldousdev.dockflowbackend.workflow.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
public class WorkflowInstanceResponse {
    private Long id;
    private Long documentId;
    private Long templateId;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String initiatedByName;
    private List<TaskResponse> tasks;
}
