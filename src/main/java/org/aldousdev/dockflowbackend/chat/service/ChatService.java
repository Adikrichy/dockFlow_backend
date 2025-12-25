package org.aldousdev.dockflowbackend.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.auth.exceptions.CompanyNotFoundException;
import org.aldousdev.dockflowbackend.auth.repository.CompanyRepository;
import org.aldousdev.dockflowbackend.auth.service.impls.AuthServiceImpl;
import org.aldousdev.dockflowbackend.chat.dto.ChatMessageDTO;
import org.aldousdev.dockflowbackend.chat.dto.response.ChatChannelResponse;
import org.aldousdev.dockflowbackend.chat.dto.response.MessageResponse;
import org.aldousdev.dockflowbackend.chat.entity.ChatChannel;
import org.aldousdev.dockflowbackend.chat.entity.Message;
import org.aldousdev.dockflowbackend.chat.repository.ChatChannelRepository;
import org.aldousdev.dockflowbackend.chat.repository.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {
    private final ChatChannelRepository chatChannelRepository;
    private final MessageRepository messageRepository;
    private final CompanyRepository companyRepository;
    private final AuthServiceImpl authService;

    /**
     * Получить все каналы компании
     */
    public List<ChatChannelResponse> getCompanyChannels(Long companyId) {
        log.info("Fetching channels for company: {}", companyId);
        
        var company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CompanyNotFoundException("Company not found"));
        
        return chatChannelRepository.findByCompanyAndIsPublicTrue(company).stream()
                .map(this::channelToResponse)
                .toList();
    }

    /**
     * Получить канал с историей сообщений
     */
    @Transactional
    public ChatChannelResponse getChannelWithMessages(Long channelId) {
        log.info("Fetching channel: {} with messages", channelId);
        
        ChatChannel channel = chatChannelRepository.findById(channelId)
                .orElseThrow(() -> new RuntimeException("Channel not found"));
        
        List<Message> messages = messageRepository.findByChannel(channel);
        
        ChatChannelResponse response = channelToResponse(channel);
        response.setMessages(messages.stream()
                .map(this::messageToResponse)
                .toList());
        
        return response;
    }

    /**
     * Сохранить новое сообщение
     */
    @Transactional
    public ChatMessageDTO saveMessage(Long channelId, String content) {
        log.info("Saving message to channel: {}", channelId);
        
        User currentUser = authService.getCurrentUser();
        
        ChatChannel channel = chatChannelRepository.findById(channelId)
                .orElseThrow(() -> new RuntimeException("Channel not found"));
        
        Message message = Message.builder()
                .content(content)
                .channel(channel)
                .sender(currentUser)
                .edited(false)
                .build();
        
        message = messageRepository.save(message);
        log.info("Message saved. ID: {}, Channel: {}", message.getId(), channelId);
        
        return ChatMessageDTO.builder()
                .id(message.getId())
                .content(message.getContent())
                .senderId(currentUser.getId())
                .senderName(currentUser.getFirstName() + " " + currentUser.getLastName())
                .channelId(channelId)
                .timestamp(message.getCreatedAt())
                .type("CHAT")
                .build();
    }

    /**
     * Создать новый канал для компании
     */
    @Transactional
    public ChatChannelResponse createChannel(Long companyId, String name, String description) {
        log.info("Creating channel: {} for company: {}", name, companyId);
        
        var company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CompanyNotFoundException("Company not found"));
        
        ChatChannel channel = ChatChannel.builder()
                .name(name)
                .description(description)
                .company(company)
                .isPublic(true)
                .build();
        
        channel = chatChannelRepository.save(channel);
        log.info("Channel created. ID: {}", channel.getId());
        
        return channelToResponse(channel);
    }

    /**
     * Удалить сообщение
     */
    @Transactional
    public void deleteMessage(Long messageId) {
        log.info("Deleting message: {}", messageId);
        messageRepository.deleteById(messageId);
    }

    /**
     * Редактировать сообщение
     */
    @Transactional
    public MessageResponse editMessage(Long messageId, String newContent) {
        log.info("Editing message: {}", messageId);
        
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));
        
        message.setContent(newContent);
        message.setEdited(true);
        message = messageRepository.save(message);
        
        return messageToResponse(message);
    }

    private ChatChannelResponse channelToResponse(ChatChannel channel) {
        return ChatChannelResponse.builder()
                .id(channel.getId())
                .name(channel.getName())
                .description(channel.getDescription())
                .companyId(channel.getCompany().getId())
                .isPublic(channel.getIsPublic())
                .createdAt(channel.getCreatedAt())
                .build();
    }

    private MessageResponse messageToResponse(Message message) {
        return MessageResponse.builder()
                .id(message.getId())
                .content(message.getContent())
                .senderId(message.getSender().getId())
                .senderName(message.getSender().getFirstName() + " " + message.getSender().getLastName())
                .channelId(message.getChannel().getId())
                .createdAt(message.getCreatedAt())
                .edited(message.getEdited())
                .editedAt(message.getEditedAt())
                .build();
    }
}
