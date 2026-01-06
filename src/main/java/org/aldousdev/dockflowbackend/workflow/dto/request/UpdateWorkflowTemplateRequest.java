package org.aldousdev.dockflowbackend.workflow.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UpdateWorkflowTemplateRequest {
    private String name;
    private String description;
    private String stepsXml;
    private List<Integer> allowedRoleLevels;
    private Boolean isActive;
}
