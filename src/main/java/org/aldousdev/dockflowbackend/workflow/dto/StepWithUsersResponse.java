package org.aldousdev.dockflowbackend.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepWithUsersResponse {
    private Integer order;
    private String roleName;
    private Integer roleLevel;
    private String description;
    private boolean parallel;
    private List<UserSummaryDto> potentialUsers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserSummaryDto {
        private Long id;
        private String email;
        private String name;
    }
}
