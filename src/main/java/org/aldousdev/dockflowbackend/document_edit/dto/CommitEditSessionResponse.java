package org.aldousdev.dockflowbackend.document_edit.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CommitEditSessionResponse {
    private Long docxVersionId;
    private Long pdfVersionId;
}
