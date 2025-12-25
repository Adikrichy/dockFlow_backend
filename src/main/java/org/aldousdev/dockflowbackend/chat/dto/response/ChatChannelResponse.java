package org.aldousdev.dockflowbackend.chat.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
public class ChatChannelResponse {
    private Long id;
    private String name;
    private String description;
    private Long companyId;
    private Boolean isPublic;
    private LocalDateTime createdAt;
    private List<MessageResponse> messages;
}
