package org.aldousdev.dockflowbackend.document_edit.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class EditorConfigResponse {
    private Map<String, Object> config;
}
