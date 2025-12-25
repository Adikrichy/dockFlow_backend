package org.aldousdev.dockflowbackend.workflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowAuditLogResponse {
    private Long id;
    private String actionType;
    private String description;
    private String performedBy; // email
    private LocalDateTime createdAt;
    private String metadata;
    private String ipAddress;
}
