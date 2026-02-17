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
     * Get all company channels
     */
    public List<ChatChannelResponse> getCompanyChannels(Long companyId) {
        log.info("Fetching channels for company: {}", companyId);
        
        var company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CompanyNotFoundException("Company not found"));
        
        return chatChannelRepository.findByCompanyAndTypeAndIsPublicTrue(company, ChatChannel.ChannelType.CHANNEL).stream()
                .map(this::channelToResponse)
                .toList();
    }

    /**
     * Get channel with message history
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
     * Save new message
     */
    @Transactional
    public ChatMessageDTO saveMessage(Long channelId, String content, User currentUser) {
        log.info("Saving message to channel: {}", channelId);
        
        // User currentUser = authService.getCurrentUser(); // Removed dependency on SecurityContext
        
        ChatChannel channel = chatChannelRepository.findById(channelId)
                .orElseThrow(() -> new RuntimeException("Channel not found"));

        // Security check: ensure user is member of the company the channel belongs to
        // This method handles the bypass for AI Assistant automatically
        if (!currentUser.isMemberOf(channel.getCompany().getId())) {
            log.error("Access denied: User {} is not a member of company {}", currentUser.getId(), channel.getCompany().getId());
            throw new RuntimeException("Access denied to this channel");
        }
        
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
                .senderEmail(currentUser.getEmail())
                .channelId(channelId)
                .timestamp(message.getCreatedAt() != null ? message.getCreatedAt() : java.time.LocalDateTime.now())
                .type("CHAT")
                .build();
    }

    /**
     * Create new channel for company
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
                .type(ChatChannel.ChannelType.CHANNEL)
                .build();
        
        channel = chatChannelRepository.save(channel);
        log.info("Channel created. ID: {}", channel.getId());
        
        return channelToResponse(channel);
    }

    /**
     * Delete message
     */
    @Transactional
    public void deleteMessage(Long messageId) {
        log.info("Deleting message: {}", messageId);
        messageRepository.deleteById(messageId);
    }

    /**
     * Delete entire channel
     */
    @Transactional
    public void deleteChannel(Long channelId) {
        log.info("Deleting channel: {}", channelId);
        chatChannelRepository.deleteById(channelId);
    }

    /**
     * Edit message
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

    private final org.aldousdev.dockflowbackend.auth.repository.UserRepository userRepository;

    /**
     * Get or create DM with user within a specific company
     */
    @Transactional
    public ChatChannelResponse getOrCreateDM(Long companyId, Long targetUserId) {
        User currentUser = authService.getCurrentUser();
        var company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CompanyNotFoundException("Company not found"));

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("Target user not found"));

        // Check if DM exists within THIS company
        return chatChannelRepository.findDMChannel(currentUser, targetUser, company)
                .map(channel -> channelToResponse(channel, currentUser))
                .orElseGet(() -> {
                    // Create new DM pinned to this company
                    ChatChannel dm = ChatChannel.builder()
                            .name("DM")
                            .type(ChatChannel.ChannelType.DM)
                            .members(List.of(currentUser, targetUser))
                            .isPublic(false)
                            .company(company)
                            .build();

                    dm = chatChannelRepository.save(dm);
                    return channelToResponse(dm, currentUser);
                });
    }

    /**
     * Get user's DM list for a specific company
     */
    public List<ChatChannelResponse> getUserDMs(Long companyId) {
        User currentUser = authService.getCurrentUser();
        var company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CompanyNotFoundException("Company not found"));
                
        return chatChannelRepository.findUserDMs(currentUser, company).stream()
                .map(channel -> channelToResponse(channel, currentUser))
                .toList();
    }

    public Object getAiUser() {
        return userRepository.findByEmail("ai@dockflow.com")
                .map(u -> java.util.Map.of(
                        "id", u.getId(),
                        "firstName", u.getFirstName(),
                        "lastName", u.getLastName(),
                        "email", u.getEmail()
                ))
                .orElseThrow(() -> new RuntimeException("AI User not initialized"));
    }

    private ChatChannelResponse channelToResponse(ChatChannel channel) {
        return channelToResponse(channel, null);
    }
    
    private ChatChannelResponse channelToResponse(ChatChannel channel, User currentUser) {
        String name = channel.getName();
        if (channel.getType() == ChatChannel.ChannelType.DM && currentUser != null) {
            // Find the other member to name the channel
            name = channel.getMembers().stream()
                    .filter(m -> !m.getId().equals(currentUser.getId()))
                    .findFirst()
                    .map(u -> u.getFirstName() + " " + u.getLastName())
                    .orElse("Unknown User");
        }

        return ChatChannelResponse.builder()
                .id(channel.getId())
                .name(name)
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
