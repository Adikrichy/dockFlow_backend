package org.aldousdev.dockflowbackend.auth.components;

import lombok.RequiredArgsConstructor;
import org.aldousdev.dockflowbackend.auth.security.JwtAuthenticationToken;
import org.aldousdev.dockflowbackend.auth.security.JWTService;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class RoleLevelAspect {

    private final JWTService jwtService;

    @Before("@annotation(requiresRoleLevel)")
    public void checkRoleLevel(RequiresRoleLevel requiresRoleLevel) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken)) {
            throw new SecurityException("Unauthorized access or invalid authentication type");
        }

        JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) authentication;
        String token = jwtAuth.getToken();

        if (token == null) {
            throw new SecurityException("Authentication token is missing");
        }

        Integer roleLevel = jwtService.extractCompanyRoleLevel(token);

        if (roleLevel == null) {
            throw new SecurityException("User is not associated with any company context");
        }

        if (roleLevel < requiresRoleLevel.value()) {
            String message = requiresRoleLevel.message().isBlank()
                    ? "Insufficient permissions: required role level " + requiresRoleLevel.value() + ", current level: " + roleLevel
                    : requiresRoleLevel.message();
            throw new SecurityException(message);
        }
    }
}