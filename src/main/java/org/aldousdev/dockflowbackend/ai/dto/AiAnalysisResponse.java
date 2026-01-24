package org.aldousdev.dockflowbackend.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AiAnalysisResponse {
    @JsonProperty("document_id")
    private Long documentId;
    @JsonProperty("version_id")
    private Long versionId;
    @JsonProperty("status")
    private String status; // PENDING | PROCESSING | SUCCESS | ERROR
    @JsonProperty("summary")
    private String summary;
    @JsonProperty("raw_result")
    private String rawResult;
    @JsonProperty("error")
    private String error;
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}