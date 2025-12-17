package org.aldousdev.dockflowbackend.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class CreateRoleResponse {
    private Long id;
    private String name;
    private Integer level;
    private Boolean isSystem;
}
