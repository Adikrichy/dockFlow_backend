package org.aldousdev.dockflowbackend.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UpdateRoleResponse {
    private Long id;
    private String name;
    private Integer roleLevel;
    private Boolean isSystem;
}
