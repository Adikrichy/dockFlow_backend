package org.aldousdev.dockflowbackend.workflow.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiWorkflowSuggestRequest {
    private String prompt;
    private String currentXml;
}
