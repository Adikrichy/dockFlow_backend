package org.aldousdev.dockflowbackend.workflow.components;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.auth.security.JWTService;
import org.aldousdev.dockflowbackend.auth.security.JwtAuthenticationToken;
import org.aldousdev.dockflowbackend.workflow.entity.WorkflowTemplate;
import org.aldousdev.dockflowbackend.workflow.repository.WorkflowTemplateRepository;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class WorkflowPermissionAspect {
    private final JWTService jwtService;
    private final WorkflowTemplateRepository workflowTemplateRepository;

    @Before("@annotation(canStartWorkflow) && args(templateId,..)")
    public void checkWorkflowPermission(JoinPoint joinPoint, CanStartWorkflow canStartWorkflow, Long templateId) {
        log.info("================================================");
        log.info("=== WORKFLOW PERMISSION CHECK STARTED ===");
        log.info("Template ID: {}", templateId);
        log.info("================================================");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        log.info("Step 1: Authentication check");
        log.info("  - User: {}", authentication != null ? authentication.getName() : "null");

        if(!(authentication instanceof JwtAuthenticationToken)){
            log.error("Failed: Not Authenticated");
            throw new SecurityException("Unauthorized access");
        }

        log.info(" -- Okay Authentication");

        log.info("Step 2: Get HTTP request");
        HttpServletRequest request = getCurrentRequest();
        if(request == null){
            log.error("Failed: request is null");
            throw new SecurityException("Invalid request");
        }

        log.info("OK Request: {}", request);

        log.info("Step 3: Extract JWT from cookie");
        String jwtWithCompany = extractJwtFromCookie(request);
        if(jwtWithCompany == null || !jwtService.isTokenValid(jwtWithCompany)){
            log.error("Failed: Invalid JWT");
            throw new SecurityException("Company context token not found or invalid");
        }

        log.info(" JWT OK: {}", jwtWithCompany);

        log.info("Step 4: Extract userRoleLevel from JWT");
        Integer userRoleLevel = jwtService.extractCompanyRoleLevel(jwtWithCompany);
        log.info("- User role level: {}\", userRoleLevel");

        if(userRoleLevel == null){
            log.error("Failed:userRoleLevel is null");
            throw new SecurityException("User role level not found");
        }

        log.info(" User role level: {}", userRoleLevel);

        log.info("Step 5: Fetch workflow template");
        WorkflowTemplate workflowTemplate = workflowTemplateRepository.findById(templateId)
                .orElseThrow(()-> {
                    log.error("Failed: Template not found");
                    throw new SecurityException("Template not found {}" + templateId);
                });

        log.info("  - Template name: {}", workflowTemplate.getName());
        log.info("  - Allowed role levels: {}", workflowTemplate.getAllowedRoleLevels() != null
                ? Arrays.toString(workflowTemplate.getAllowedRoleLevels())
                : "null (default: CEO only)");
        log.info(" Template found: {}", workflowTemplate);

        log.info("Step:6 checks permission");
        boolean canStart = workflowTemplate.canStartWorkflow(userRoleLevel);
        log.info("  - Can user start workflow: {}", canStart);
        log.info("  - Comparison: user level {} vs allowed levels {}",
                userRoleLevel,
                workflowTemplate.getAllowedRoleLevels() != null
                        ? Arrays.toString(workflowTemplate.getAllowedRoleLevels())
                        : "[100]");
        if(!canStart){
            String message = canStartWorkflow.message().isBlank()
                    ? String.format("You don't have permission to start workflow '%s'. " +
                    "Required role level: %s, your level: %d",
                    workflowTemplate.getName(),
                    workflowTemplate.getAllowedRoleLevels() != null
                        ? Arrays.toString(workflowTemplate.getAllowedRoleLevels())
                        : "[100]",
                        userRoleLevel
                    ):canStartWorkflow.message();

            log.error("================================================");
            log.error("=== WORKFLOW START DENIED ===");
            log.error("Reason: Insufficient permissions");
            log.error("Message: {}", message);
            log.error("================================================");
            throw new SecurityException(message);

        }

        log.info("================================================");
        log.info("=== WORKFLOW START GRANTED ===");
        log.info("User {} (level {}) can start workflow '{}'",
                authentication.getName(), userRoleLevel,  workflowTemplate.getName());
        log.info("================================================");

    }

    private HttpServletRequest getCurrentRequest(){
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    private String extractJwtFromCookie(HttpServletRequest request) {
        if(request.getCookies() == null){
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(c->"jwtWithCompany".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
