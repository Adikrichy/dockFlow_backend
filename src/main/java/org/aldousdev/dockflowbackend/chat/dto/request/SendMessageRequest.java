package org.aldousdev.dockflowbackend.chat.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SendMessageRequest {
    private Long channelId;
    private String content;
}
