package org.aldousdev.dockflowbackend.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.auth.dto.request.telegram.TelegramBindingSuccessEvent;
import org.aldousdev.dockflowbackend.auth.repository.TelegramBindingRepository;
import org.aldousdev.dockflowbackend.config.RabbitMQTelegramConfig;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@Slf4j
public class TelegramMessageConsumer {

    private final TelegramBindingRepository telegramBindingRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper telegramObjectMapper;

    public TelegramMessageConsumer(TelegramBindingRepository telegramBindingRepository,
                                   RabbitTemplate rabbitTemplate) {
        this.telegramBindingRepository = telegramBindingRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.telegramObjectMapper = new ObjectMapper();
    }

    @RabbitListener(queues = RabbitMQTelegramConfig.TELEGRAM_BINDING_QUEUE)
    @Transactional
    public void handleBindingSuccess(Message message) {
        try {
            String json = new String(message.getBody());
            log.info("Raw Telegram binding message received: {}", json);

            TelegramBindingSuccessEvent event = telegramObjectMapper.readValue(json, TelegramBindingSuccessEvent.class);

            log.info("Parsed Telegram binding event — token: {}, telegramId: {}", 
                    event.getToken(), event.getTelegramId());

            if (event.getToken() == null || event.getTelegramId() == null) {
                log.error("Invalid binding event: token or telegramId is null. Raw JSON: {}", json);
                return;
            }

            telegramBindingRepository.findByLinkingToken(event.getToken())
                    .ifPresentOrElse(binding -> {
                        if (binding.getTokenExpiresAt().isBefore(LocalDateTime.now())) {
                            log.warn("Linking token expired for token: {}", event.getToken());
                            sendBotMessage(event.getTelegramId(),
                                    "⏰ Срок действия ссылки истёк.\n" +
                                    "Пожалуйста, сгенерируйте новую на странице настроек профиля.");
                            return;
                        }

                        // STRICT MODE: Check if this Telegram is already linked to ANOTHER user
                        var existingOpt = telegramBindingRepository.findByTelegramId(event.getTelegramId());
                        if (existingOpt.isPresent() && !existingOpt.get().getId().equals(binding.getId())) {
                            String ownerEmail = existingOpt.get().getUser().getEmail();
                            // Mask email for privacy: "a***l@gmail.com"
                            String maskedEmail = maskEmail(ownerEmail);
                            
                            log.warn("Telegram ID {} is already linked to user {}. Rejecting binding for token {}.",
                                    event.getTelegramId(), ownerEmail, event.getToken());
                            
                            sendBotMessage(event.getTelegramId(),
                                    "🚫 Этот Telegram уже привязан к другому аккаунту DockFlow (" + maskedEmail + ").\n\n" +
                                    "Чтобы привязать к новому аккаунту:\n" +
                                    "1. Войдите в старый аккаунт на сайте\n" +
                                    "2. Перейдите в Настройки → Интеграция с Telegram\n" +
                                    "3. Нажмите «Отвязать аккаунт от Telegram»\n" +
                                    "4. После этого повторите привязку с нового аккаунта");
                            return;
                        }

                        binding.setTelegramId(event.getTelegramId());
                        binding.setLinkingToken(null);
                        binding.setTokenExpiresAt(null);
                        telegramBindingRepository.saveAndFlush(binding);
                        
                        log.info("Successfully linked Telegram ID {} to user {}", 
                                event.getTelegramId(), binding.getUser().getEmail());
                        
                        sendBotMessage(event.getTelegramId(),
                                "✅ Аккаунт успешно привязан!\n\n" +
                                "Теперь вы будете получать приглашения в компании и системные уведомления прямо в этот чат.");
                                
                    }, () -> {
                        log.warn("Linking token not found: {}", event.getToken());
                        sendBotMessage(event.getTelegramId(),
                                "❌ Токен привязки не найден или уже использован.\n" +
                                "Пожалуйста, сгенерируйте новый на странице настроек профиля.");
                    });

        } catch (Exception e) {
            log.error("Failed to process Telegram binding message: {}", e.getMessage(), e);
        }
    }

    /**
     * Send a message to the user via Telegram bot using the send queue.
     */
    private void sendBotMessage(Long telegramId, String text) {
        try {
            String payload = telegramObjectMapper.writeValueAsString(Map.of(
                    "telegramId", telegramId,
                    "message", text,
                    "parseMode", ""
            ));
            
            Message msg = MessageBuilder
                    .withBody(payload.getBytes(StandardCharsets.UTF_8))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .build();
            
            rabbitTemplate.send(RabbitMQTelegramConfig.TELEGRAM_EXCHANGE, 
                    "dockflow.telegram.send.notify", msg);
            
            log.info("Sent bot response to Telegram ID {}", telegramId);
        } catch (Exception e) {
            log.error("Failed to send bot message to {}: {}", telegramId, e.getMessage());
        }
    }

    private String maskEmail(String email) {
        int atIdx = email.indexOf('@');
        if (atIdx <= 2) return email;
        return email.charAt(0) + "***" + email.substring(atIdx - 1);
    }
}

