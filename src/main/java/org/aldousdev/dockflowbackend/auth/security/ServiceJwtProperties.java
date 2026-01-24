package org.aldousdev.dockflowbackend.auth.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "security.service-jwt")
public class ServiceJwtProperties {
    private String secret;
    private String issuer = "dockflow-core";
    private String audience = "dockflow-ai";
    private int ttlSeconds = 300;
    private List<String> allowedServices = List.of("service:dockflow-ai");
}
