package org.aldousdev.dockflowbackend.auth.dto.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateRoleRequest {
    @NotBlank(message = "Role name is required")
    @Size(min = 2, max = 50, message = "Role name must be between 2 and 50 characters")
    private String roleName;

    @NotNull
    @Min(10) @Max(100)
    private Integer roleLevel;
}
