package org.aldousdev.dockflowbackend.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.ai.producer.AiTaskProducer;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.chat.entity.ChatChannel;
import org.aldousdev.dockflowbackend.chat.repository.ChatChannelRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiChatService {

    private final AiTaskProducer aiTaskProducer;
    private final ChatChannelRepository chatChannelRepository;

    /**
     * Name of the special AI User.
     * We assume there's a user in the system representing AI, or we just check channel type/name.
     * For now, let's assume if it's a DM and the other participant is named "AI Assistant" or email "ai@dockflow.com".
     * OR simpler: check if the target user ID is a specific constant or configured value.
     *
     * Let's take a flexible approach: Check if any member of the channel has email "ai@dockflow.com".
     */
    private static final String AI_EMAIL = "ai@dockflow.com";

    @Async
    @Transactional(readOnly = true)
    public void processMessage(Long channelId, String content, User sender) {
        log.debug("Checking if message in channel {} needs AI processing", channelId);

        if (sender.getEmail().equalsIgnoreCase(AI_EMAIL)) {
            // Don't reply to yourself
            return;
        }

        Optional<ChatChannel> channelOpt = chatChannelRepository.findById(channelId);
        if (channelOpt.isEmpty()) {
            return;
        }

        ChatChannel channel = channelOpt.get();

        // Check if this is a DM with AI
        boolean isAiChannel = false;
        if (channel.getType() == ChatChannel.ChannelType.DM) {
            isAiChannel = channel.getMembers().stream()
                    .anyMatch(u -> AI_EMAIL.equalsIgnoreCase(u.getEmail()));
        } else {
            // For public channels, maybe check for mention "@AI" (future scope)
            // For now only DMs
        }

        if (isAiChannel) {
            log.info("Message in channel {} is targeting AI. Sending to AI Service...", channelId);
            aiTaskProducer.sendChatMessage(content, channelId, sender.getId(), sender.getFirstName(), "GENERAL");
        }
    }
}
