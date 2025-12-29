package org.aldousdev.dockflowbackend.workflow.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class BulkOperationResponse {
    private int totalTasks;
    private int successfulCount;
    private List<Long> successfulTaskIds;
    private List<String> errors;
}
