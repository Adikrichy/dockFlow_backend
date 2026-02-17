package org.aldousdev.dockflowbackend.auth.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CompanyResponse {
    private Long id;
    private String name;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @com.fasterxml.jackson.annotation.JsonProperty("preferredEditor")
    private org.aldousdev.dockflowbackend.document_edit.enums.EditorType preferredEditor;
}
