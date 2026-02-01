package org.aldousdev.dockflowbackend.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO {
    private Long id;
    private String content;
    private Long senderId;
    private String senderName;
    private Long channelId;
    private LocalDateTime timestamp;
    private String type; // "CHAT", "SYSTEM", etc.
    private String status; // "sent", "error", etc.
    @JsonProperty("isAi")
    private boolean isAi;
}
