package org.aldousdev.dockflowbackend.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @NotBlank
    private String token;

    @NotBlank
    @Size(min = 12, message = "Password must be at least 12 characters")
    private String newPassword;
}
