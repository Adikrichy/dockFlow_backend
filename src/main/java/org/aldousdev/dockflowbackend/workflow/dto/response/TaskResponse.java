package org.aldousdev.dockflowbackend.workflow.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class TaskResponse {
    private Long id;
    private Integer stepOrder;
    private String requiredRoleName;
    private Integer requiredRoleLevel;
    private String status;
    private String comment;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String completedByName;

    private DocumentInfo document;

    @Getter
    @Setter
    @Builder
    public static class DocumentInfo {
        private Long id;
        private String filename;
    }
}
