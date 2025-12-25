package org.aldousdev.dockflowbackend.auth.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterResponse {
    private Long userId;
    private String firstName;
    private String lastName;
    private String status;
}
