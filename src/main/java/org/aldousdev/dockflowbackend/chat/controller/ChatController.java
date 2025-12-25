package org.aldousdev.dockflowbackend.chat.controller;

import lombok.RequiredArgsConstructor;
import org.aldousdev.dockflowbackend.chat.dto.response.ChatChannelResponse;
import org.aldousdev.dockflowbackend.chat.service.ChatService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;

    /**
     * Получить все каналы компании
     */
    @GetMapping("/company/{companyId}/channels")
    public ResponseEntity<List<ChatChannelResponse>> getCompanyChannels(
            @PathVariable Long companyId) {
        List<ChatChannelResponse> channels = chatService.getCompanyChannels(companyId);
        return ResponseEntity.ok(channels);
    }

    /**
     * Получить канал с историей сообщений
     */
    @GetMapping("/channel/{channelId}")
    public ResponseEntity<ChatChannelResponse> getChannel(
            @PathVariable Long channelId) {
        ChatChannelResponse channel = chatService.getChannelWithMessages(channelId);
        return ResponseEntity.ok(channel);
    }

    /**
     * Создать новый канал
     */
    @PostMapping("/company/{companyId}/channels")
    public ResponseEntity<ChatChannelResponse> createChannel(
            @PathVariable Long companyId,
            @RequestParam String name,
            @RequestParam(required = false) String description) {
        ChatChannelResponse channel = chatService.createChannel(companyId, name, description);
        return ResponseEntity.status(HttpStatus.CREATED).body(channel);
    }

    /**
     * Удалить сообщение
     */
    @DeleteMapping("/message/{messageId}")
    public ResponseEntity<Void> deleteMessage(
            @PathVariable Long messageId) {
        chatService.deleteMessage(messageId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Отредактировать сообщение
     */
    @PatchMapping("/message/{messageId}")
    public ResponseEntity<?> editMessage(
            @PathVariable Long messageId,
            @RequestParam String content) {
        var message = chatService.editMessage(messageId, content);
        return ResponseEntity.ok(message);
    }
}
