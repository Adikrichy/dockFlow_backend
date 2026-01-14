package org.aldousdev.dockflowbackend.reports.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ReportDataDTO {
    private Long totalDocuments;
    private Long totalVersions;
    private Long pendingDocuments;
    private Long approvedDocuments;
    private Long rejectedDocuments;
    private Double averageProcessingTime;
    private Long totalUsers;
    private Long activeUsers;
    private List<Map<String, Object>> weeklyData;
    private List<Map<String, Object>> userActivity;
    private List<Map<String, Object>> documentTypes;
}
