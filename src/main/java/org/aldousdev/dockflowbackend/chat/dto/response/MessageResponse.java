package org.aldousdev.dockflowbackend.chat.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class MessageResponse {
    private Long id;
    private String content;
    private Long senderId;
    private String senderName;
    private Long channelId;
    private LocalDateTime createdAt;
    private Boolean edited;
    private LocalDateTime editedAt;
}
