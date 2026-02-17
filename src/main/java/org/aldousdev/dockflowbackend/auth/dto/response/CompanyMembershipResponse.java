package org.aldousdev.dockflowbackend.auth.dto.response;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CompanyMembershipResponse {
    private Long companyId;
    private String companyName;
    private String description;
    private String roleName;
    private Integer roleLevel;
    @com.fasterxml.jackson.annotation.JsonProperty("preferredEditor")
    private org.aldousdev.dockflowbackend.document_edit.enums.EditorType preferredEditor;
}
