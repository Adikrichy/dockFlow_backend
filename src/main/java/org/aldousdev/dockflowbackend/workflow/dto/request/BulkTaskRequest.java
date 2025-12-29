package org.aldousdev.dockflowbackend.workflow.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class BulkTaskRequest {
    private List<Long> taskIds;
    private String comment;
}
