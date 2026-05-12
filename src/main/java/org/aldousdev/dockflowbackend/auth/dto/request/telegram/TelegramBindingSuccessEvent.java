package org.aldousdev.dockflowbackend.auth.dto.request.telegram;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelegramBindingSuccessEvent {
    private String token;
    private Long telegramId;
    private String firstName;
    private String lastName;
    private String username;
}
