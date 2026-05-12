package org.aldousdev.dockflowbackend.dashboard.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class DashboardStatsResponse {
    private long totalDocuments;
    private long activeWorkflows;
    private long pendingTasks;
}
