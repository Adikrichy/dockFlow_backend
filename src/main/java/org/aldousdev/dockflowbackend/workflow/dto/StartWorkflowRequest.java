package org.aldousdev.dockflowbackend.workflow.dto;

import lombok.Data;
import java.util.Map;

/**
 * Request DTO for starting a workflow with optional direct user assignments.
 */
@Data
public class StartWorkflowRequest {
    /**
     * ID of the document to start the workflow for.
     */
    private Long documentId;

    /**
     * Optional map of step order to user ID for direct assignment.
     * If a step is in this map, the task will be assigned directly to that user.
     * If not, the task will be broadcast to all users with the required role (current behavior).
     */
    private Map<Integer, Long> stepAssignments;
}
