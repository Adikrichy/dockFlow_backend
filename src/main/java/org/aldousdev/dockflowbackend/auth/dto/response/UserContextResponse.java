package org.aldousdev.dockflowbackend.auth.dto.response;

import lombok.*;

import java.util.List;
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserContextResponse{
    private UserResponse user;
    private List<CompanyMembershipResponse> companies;
    private CompanyMembershipResponse currentCompany;
}
