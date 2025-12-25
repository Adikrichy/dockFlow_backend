package org.aldousdev.dockflowbackend.auth.dto.response;

import lombok.Getter;
import lombok.Setter;
import org.aldousdev.dockflowbackend.auth.entity.Company;

@Getter
@Setter
public class CreateCompanyResponse {
    private CompanyResponse company;
    private String jwt;
}
