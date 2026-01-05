package org.aldousdev.dockflowbackend.auth.components;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.auth.security.JwtAuthenticationToken;
import org.aldousdev.dockflowbackend.auth.security.JWTService;
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
public class RoleLevelAspect {

    private final JWTService jwtService;

    @Before("@annotation(requiresRoleLevel)")
    public void checkRoleLevel(RequiresRoleLevel requiresRoleLevel) {
        log.info("================================================");
        log.info("=== ROLE LEVEL CHECK STARTED ===");
        log.info("================================================");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        log.info("Step 1: Authentication check");
        log.info("  - User: {}", authentication != null ? authentication.getName() : "null");
        log.info("  - Auth type: {}", authentication != null ? authentication.getClass().getSimpleName() : "null");

        if (!(authentication instanceof JwtAuthenticationToken)) {
            log.error("FAILED: Not JwtAuthenticationToken");
            throw new SecurityException("Unauthorized access or invalid authentication type");
        }
        log.info("  - ✓ Authentication OK");

        log.info("Step 2: Get HTTP request");
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            log.error("FAILED: Request is null");
            throw new SecurityException("Unauthorized access or invalid request");
        }
        log.info("  - ✓ Request OK");

        log.info("Step 3: Extract JWT from cookie");
        if (request.getCookies() != null) {
            log.info("  - Available cookies: {}",
                    Arrays.stream(request.getCookies())
                            .map(Cookie::getName)
                            .reduce((a, b) -> a + ", " + b)
                            .orElse("none"));
        } else {
            log.warn("  - No cookies in request");
        }

        String jwtWithCompany = extractJwtFromCookie(request);
        log.info("  - jwtWithCompany found: {}", jwtWithCompany != null);

        if (jwtWithCompany == null) {
            log.error("FAILED: jwtWithCompany cookie not found");
            throw new SecurityException("Company context token not found");
        }

        boolean isValid = jwtService.isTokenValid(jwtWithCompany);
        log.info("  - Token valid: {}", isValid);

        if (!isValid) {
            log.error("FAILED: Token is invalid");
            throw new SecurityException("Invalid JWT token");
        }
        log.info("  - ✓ JWT OK");

        log.info("Step 4: Extract role level from token");
        Integer roleLevel = jwtService.extractCompanyRoleLevel(jwtWithCompany);
        log.info("  - Extracted roleLevel: {}", roleLevel);
        log.info("  - roleLevel type: {}", roleLevel != null ? roleLevel.getClass().getName() : "null");

        if (roleLevel == null) {
            log.error("FAILED: Role level is null");
            throw new SecurityException("User role level not found");
        }
        log.info("  - ✓ Role level extracted");

        int requiredRoleLevel = requiresRoleLevel.value();

        log.info("Step 5: Compare levels");
        log.info("  - Required level: {} (type: {})", requiredRoleLevel, Integer.class.getName());
        log.info("  - User level:     {} (type: {})", roleLevel, roleLevel.getClass().getName());
        log.info("  - Comparison expression: {} < {}", roleLevel, requiredRoleLevel);
        log.info("  - Result: {}", (roleLevel < requiredRoleLevel));

        if (roleLevel < requiredRoleLevel) {
            String message = requiresRoleLevel.message().isBlank()
                    ? "Insufficient permissions: required " + requiredRoleLevel + ", has " + roleLevel
                    : requiresRoleLevel.message();
            log.error("================================================");
            log.error("=== ACCESS DENIED ===");
            log.error("Message: {}", message);
            log.error("================================================");
            throw new SecurityException(message);
        }

        log.info("================================================");
        log.info("=== ACCESS GRANTED ===");
        log.info("User {} with level {} accessed endpoint requiring level {}",
                authentication.getName(), roleLevel, requiredRoleLevel);
        log.info("================================================");
    }

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attribute = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attribute != null ? attribute.getRequest() : null;
    }

    private String extractJwtFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }

        return Arrays.stream(request.getCookies())
                .filter(c -> "jwtWithCompany".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}