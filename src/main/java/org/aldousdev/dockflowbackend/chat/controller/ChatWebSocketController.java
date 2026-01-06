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
    private final org.aldousdev.dockflowbackend.auth.repository.UserRepository userRepository;

    /**
     * WebSocket endpoint for sending messages
     * Client sends to: /app/chat.send/channelId/{channelId}
     * Message will be sent to: /topic/channel/{channelId}
     */
    @MessageMapping("/chat.send/channelId/{channelId}")
    @SendTo("/topic/channel/{channelId}")
    public ChatMessageDTO sendMessage(
            @DestinationVariable Long channelId,
            SendMessageRequest request,
            java.security.Principal principal) {
        
        log.info("WebSocket message received in channel: {}", channelId);
        
        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            log.warn("Empty message attempt");
            return null;
        }

        if (principal == null) {
             log.error("No principal found in WebSocket message for channel: {}", channelId);
             throw new RuntimeException("Unauthorized");
        }
        
        org.aldousdev.dockflowbackend.auth.entity.User user = null;
        
        // Try to get User from Authentication object
        if (principal instanceof org.springframework.security.core.Authentication auth) {
            if (auth.getPrincipal() instanceof org.aldousdev.dockflowbackend.auth.entity.User u) {
                user = u;
            }
        }
        
        // Fallback: search by name (email) if principal is just a simple Principal object
        if (user == null) {
             user = userRepository.findByEmail(principal.getName())
                     .orElseThrow(() -> new RuntimeException("User not found in Principal and database: " + principal.getName()));
        }

        log.info("Message from User: {} (Email: {}). Requested senderId: {}", 
                user.getId(), user.getEmail(), request.getSenderId());

        if (request.getSenderId() != null && !request.getSenderId().equals(user.getId())) {
            log.warn("SESSION MISMATCH: UI thinks it's user {}, but session is user {}", 
                    request.getSenderId(), user.getId());
        }

        return chatService.saveMessage(channelId, request.getContent(), user);
    }

    /**
     * Alternative format with message in body
     */
    @MessageMapping("/chat/{channelId}")
    @SendTo("/topic/channel/{channelId}")
    public ChatMessageDTO sendChatMessage(
            @DestinationVariable Long channelId,
            SendMessageRequest request,
            java.security.Principal principal) {
        
        log.info("WebSocket message received in channel: {}", channelId);
        
        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            log.warn("Empty message attempt");
            return null;
        }

        if (principal == null) {
            log.error("No principal found in WebSocket message");
            throw new RuntimeException("Unauthorized");
        }

        org.aldousdev.dockflowbackend.auth.entity.User user = null;
        if (principal instanceof org.springframework.security.core.Authentication auth) {
            if (auth.getPrincipal() instanceof org.aldousdev.dockflowbackend.auth.entity.User u) {
                user = u;
            }
        }

        if (user == null) {
            log.warn("User not found in Principal class ({}), searching by name: {}", principal.getClass().getName(), principal.getName());
            user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new RuntimeException("User not found in Principal and database: " + principal.getName()));
        }

        return chatService.saveMessage(channelId, request.getContent(), user);
    }
}
