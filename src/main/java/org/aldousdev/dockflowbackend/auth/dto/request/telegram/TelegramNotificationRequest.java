package org.aldousdev.dockflowbackend.auth.dto.request.telegram;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramNotificationRequest {
    private Long telegramId;
    private String message;
    private String parseMode; // markdown or html
}
