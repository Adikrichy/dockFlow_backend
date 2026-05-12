package org.aldousdev.dockflowbackend.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQTelegramConfig {

    public static final String TELEGRAM_EXCHANGE = "dockflow.telegram.exchange";
    
    // Queue for sending messages to Telegram Bot
    public static final String TELEGRAM_SEND_QUEUE = "dockflow.telegram.send.queue";
    public static final String TELEGRAM_SEND_ROUTING_KEY = "dockflow.telegram.send.#";
    
    // Queue for receiving binding success from Bot
    public static final String TELEGRAM_BINDING_QUEUE = "dockflow.telegram.binding.queue";
    public static final String TELEGRAM_BINDING_ROUTING_KEY = "dockflow.telegram.binding.success";

    @Bean
    public TopicExchange telegramExchange() {
        return new TopicExchange(TELEGRAM_EXCHANGE);
    }

    @Bean
    public Queue telegramSendQueue() {
        return QueueBuilder.durable(TELEGRAM_SEND_QUEUE).build();
    }

    @Bean
    public Queue telegramBindingQueue() {
        return QueueBuilder.durable(TELEGRAM_BINDING_QUEUE).build();
    }

    @Bean
    public Binding telegramSendBinding(Queue telegramSendQueue, TopicExchange telegramExchange) {
        return BindingBuilder.bind(telegramSendQueue).to(telegramExchange).with(TELEGRAM_SEND_ROUTING_KEY);
    }

    @Bean
    public Binding telegramBindingBinding(Queue telegramBindingQueue, TopicExchange telegramExchange) {
        return BindingBuilder.bind(telegramBindingQueue).to(telegramExchange).with(TELEGRAM_BINDING_ROUTING_KEY);
    }
}
