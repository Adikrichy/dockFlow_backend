package org.aldousdev.dockflowbackend.auth.dto.response;

import lombok.Getter;
import lombok.Setter;
import org.aldousdev.dockflowbackend.auth.entity.User;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserResponse {
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private String userType;
    private String companyRole;
}
