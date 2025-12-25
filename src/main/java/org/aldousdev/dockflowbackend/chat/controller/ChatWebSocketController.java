package org.aldousdev.dockflowbackend.chat.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.chat.dto.ChatMessageDTO;
import org.aldousdev.dockflowbackend.chat.dto.request.SendMessageRequest;
import org.aldousdev.dockflowbackend.chat.service.ChatService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketController {
    private final ChatService chatService;

    /**
     * WebSocket эндпоинт для отправки сообщений
     * Клиент отправляет на: /app/chat.send/channelId/{channelId}
     * Сообщение будет отправлено на: /topic/channel/{channelId}
     */
    @MessageMapping("/chat.send/channelId/{channelId}")
    @SendTo("/topic/channel/{channelId}")
    public ChatMessageDTO sendMessage(
            @DestinationVariable Long channelId,
            SendMessageRequest request) {
        
        log.info("WebSocket message received in channel: {}", channelId);
        
        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            log.warn("Empty message attempt");
            return null;
        }

        return chatService.saveMessage(channelId, request.getContent());
    }

    /**
     * Альтернативный формат с сообщением в body
     */
    @MessageMapping("/chat/{channelId}")
    @SendTo("/topic/channel/{channelId}")
    public ChatMessageDTO sendChatMessage(
            @DestinationVariable Long channelId,
            SendMessageRequest request) {
        
        log.info("WebSocket message received in channel: {}", channelId);
        
        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            log.warn("Empty message attempt");
            return null;
        }

        return chatService.saveMessage(channelId, request.getContent());
    }
}
