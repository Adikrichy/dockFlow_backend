package org.aldousdev.dockflowbackend.chat.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class ChatMessageDTO {
    private Long id;
    private String content;
    private Long senderId;
    private String senderName;
    private Long channelId;
    private LocalDateTime timestamp;
    private String type; // "CHAT", "SYSTEM", etc.
}
