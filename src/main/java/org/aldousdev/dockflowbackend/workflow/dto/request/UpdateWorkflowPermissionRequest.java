package org.aldousdev.dockflowbackend.workflow.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UpdateWorkflowPermissionRequest {
    @NotNull
    @NotEmpty(message = "Not be null")
    private List<Integer> allowedRoleLevels;
}
