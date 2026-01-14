package org.aldousdev.dockflowbackend.reports.dto;

import lombok.Data;

@Data
public class ReportFiltersDTO {
    private String timeRange;
    private String team;
    private String company;
    private String tag;
}
