package org.aldousdev.dockflowbackend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Поддержка Java 8 Date/Time API
        mapper.registerModule(new JavaTimeModule());

        // Поддержка Optional и других Java 8 типов
        mapper.registerModule(new Jdk8Module());

        // Дополнительные настройки (опционально)
        // mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        return mapper;
    }
}