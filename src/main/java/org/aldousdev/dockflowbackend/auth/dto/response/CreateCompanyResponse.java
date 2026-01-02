package org.aldousdev.dockflowbackend.auth.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCompanyResponse {
    private CompanyResponse company;
    private String jwt;
    private String keyFileBase64; // PKCS#12 key file encoded as Base64 for JSON response
}
