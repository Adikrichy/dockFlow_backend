package org.aldousdev.dockflowbackend.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiRabbitConfig {

    public static final String AI_TASK_QUEUE = "ai_tasks";
    public static final String CORE_RESULTS_QUEUE = "dockflow.core.ai_results";

    @Bean
    public Queue aiTaskQueue() {
        return QueueBuilder.durable(AI_TASK_QUEUE).build();
    }

    @Bean
    public Queue coreResultsQueue() {
        return QueueBuilder.durable(CORE_RESULTS_QUEUE).build();
    }

    @Bean
    public ObjectMapper aiObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * NOTE:
     * Jackson2JsonMessageConverter is deprecated in Spring AMQP 4.x,
     * but still required because the new JacksonJsonMessageConverter
     * expects tools.jackson JsonMapper instead of com.fasterxml ObjectMapper.
     *
     * Migration planned after full Jackson 3 alignment.
     */

    @Bean
    @SuppressWarnings("deprecation")
    public Jackson2JsonMessageConverter aiJsonConverter(ObjectMapper aiObjectMapper) {
        return new Jackson2JsonMessageConverter(aiObjectMapper);
    }

    @Bean
    public RabbitTemplate aiRabbitTemplate(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter aiJsonConverter
    ) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(aiJsonConverter);
        return template;
    }
}
