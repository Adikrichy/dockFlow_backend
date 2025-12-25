package org.aldousdev.dockflowbackend.workflow.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateWorkflowTemplateRequest {
    private String name;
    private String description;
    private String stepsXml; // XML с описанием маршрута
    private Long companyId;
}
