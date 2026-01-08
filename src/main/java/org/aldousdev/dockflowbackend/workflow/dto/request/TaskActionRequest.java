package org.aldousdev.dockflowbackend.workflow.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.aldousdev.dockflowbackend.workflow.enums.ActionType;

@Data
@Schema(description = "Запрос на выполнение действия над задачей")
public class TaskActionRequest {
    @Schema(description = "Тип действия", required = true)
    private ActionType actionType; // DELEGATE, REQUEST_CHANGES, HOLD, etc.
    
    @Schema(description = "Комментарий к действию")
    private String comment;
    
    @Schema(description = "ID пользователя для делегирования (если применимо)")
    private Long targetUserId;
}
