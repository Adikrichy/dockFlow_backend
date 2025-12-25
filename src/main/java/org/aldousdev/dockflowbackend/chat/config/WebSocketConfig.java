package org.aldousdev.dockflowbackend.chat.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Включаем простой message broker с префиксом /topic
        config.enableSimpleBroker("/topic", "/queue");
        // Устанавливаем префикс для @MessageMapping
        config.setApplicationDestinationPrefixes("/app");
        // Пользовательские очереди
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket endpoint для подключения клиентов
        registry.addEndpoint("/ws/chat")
                .setAllowedOrigins("*");
    }
}
