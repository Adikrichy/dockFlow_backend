package org.aldousdev.dockflowbackend.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.config.RabbitMQTelegramConfig;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
@Slf4j
public class TelegramProducer {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public TelegramProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        // Use a default camelCase ObjectMapper to match what the Python bot expects
        this.objectMapper = new ObjectMapper();
    }

    public void sendNotification(Long telegramId, String message) {
        sendMessage(telegramId, message, "", "dockflow.telegram.send.notification");
        log.info("Sent Telegram notification to ID: {}", telegramId);
    }

    public void sendInvite(Long telegramId, String message) {
        sendMessage(telegramId, message, "markdown", "dockflow.telegram.send.invite");
        log.info("Sent Telegram invite to ID: {}", telegramId);
    }

    public void sendInviteHtml(Long telegramId, String message) {
        sendMessage(telegramId, message, "HTML", "dockflow.telegram.send.invite");
        log.info("Sent Telegram HTML invite to ID: {}", telegramId);
    }

    private void sendMessage(Long telegramId, String text, String parseMode, String routingKey) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "telegramId", telegramId,
                    "message", text,
                    "parseMode", parseMode
            ));

            Message msg = MessageBuilder
                    .withBody(payload.getBytes(StandardCharsets.UTF_8))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .build();

            rabbitTemplate.send(RabbitMQTelegramConfig.TELEGRAM_EXCHANGE, routingKey, msg);
        } catch (Exception e) {
            log.error("Failed to send Telegram message to {}: {}", telegramId, e.getMessage(), e);
        }
    }
}

