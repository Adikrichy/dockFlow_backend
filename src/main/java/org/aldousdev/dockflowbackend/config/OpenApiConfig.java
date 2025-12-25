package org.aldousdev.dockflowbackend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", 
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT token")
                        )
                )
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    private Info apiInfo() {
        return new Info()
                .title("DocFlow Backend API")
                .description("""
                        DocFlow Backend - Document Workflow Management System
                        
                        ## Features
                        - User Authentication & Authorization
                        - Document Management (PDF uploads)
                        - Real-time Chat with WebSocket
                        - Advanced Workflow with XML-based routing
                        - Conditional task routing (return to previous steps)
                        - Complete audit logging
                        - Email notifications
                        
                        ## Authentication
                        All endpoints (except auth and registration) require JWT token in Authorization header:
                        `Authorization: Bearer <jwt_token>`
                        
                        ## Workflow System
                        - Create workflow templates with XML routing rules
                        - Support for sequential and parallel task steps
                        - Conditional routing: return to previous steps on rejection
                        - Full audit trail for all workflow actions
                        - WebSocket real-time status updates
                        - Email notifications for approvers
                        """)
                .version("1.0.0")
                .contact(new Contact()
                        .name("DocFlow Team")
                        .email("support@dockflow.com")
                        .url("https://dockflow.com")
                )
                .license(new License()
                        .name("MIT License")
                        .url("https://opensource.org/licenses/MIT")
                );
    }
}
