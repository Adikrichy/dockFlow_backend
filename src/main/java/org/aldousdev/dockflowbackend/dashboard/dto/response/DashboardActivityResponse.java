package org.aldousdev.dockflowbackend.dashboard.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class DashboardActivityResponse {
    private Long id;
    private String actionType;
    private String description;
    private String performedBy;
    private LocalDateTime createdAt;
    private String documentName;
}
