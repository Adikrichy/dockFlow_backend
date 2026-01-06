package org.aldousdev.dockflowbackend.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Запрос на обновление роли участника компании")
public class UpdateMemberRoleRequest {
    @NotNull(message = "User ID must not be null")
    @Schema(description = "ID пользователя, которому меняем роль")
    private Long userId;

    @NotNull(message = "Role ID must not be null")
    @Schema(description = "ID новой роли")
    private Long roleId;
}
