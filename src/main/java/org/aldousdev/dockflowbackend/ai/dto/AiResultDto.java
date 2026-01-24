package org.aldousdev.dockflowbackend.ai.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AiResultDto {

    private int schemaVersion;
    private String correlationId;
    private String createdAt;
    private String taskId;
    private String status;
    private Map<String, Object> result;
    private String error;
}
