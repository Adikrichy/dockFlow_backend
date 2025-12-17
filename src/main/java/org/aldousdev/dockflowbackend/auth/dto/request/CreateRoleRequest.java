package org.aldousdev.dockflowbackend.auth.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateRoleRequest {
    @NotBlank
    private String roleName;

    @Min(1)
    @Max(100)
    private Integer level;
}
