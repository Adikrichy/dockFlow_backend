package org.aldousdev.dockflowbackend.auth.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompanyRequest {
    private String name;
    private String description;
    private boolean useDefaultRoles;
    private String keyPassword; // Password to encrypt the PKCS#12 key file
}
