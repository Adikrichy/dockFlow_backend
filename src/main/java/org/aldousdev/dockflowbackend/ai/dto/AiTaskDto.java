package org.aldousdev.dockflowbackend.ai.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AiTaskDto {
    private int schemaVersion = 1;
    private String correlationId;
    private String createdAt;

    private String taskId = UUID.randomUUID().toString();
    private String type;
    private Map<String, Object> payload = new HashMap<>();

    private String replyTo;
}
