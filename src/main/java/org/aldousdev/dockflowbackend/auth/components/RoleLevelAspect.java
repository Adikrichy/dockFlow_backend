package org.aldousdev.dockflowbackend.auth.components;

import lombok.RequiredArgsConstructor;
import org.aldousdev.dockflowbackend.auth.entity.CompanyRoleEntity;
import org.aldousdev.dockflowbackend.auth.entity.Membership;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.auth.security.JWTService;
import org.aldousdev.dockflowbackend.auth.security.JwtAuthenticationToken;
import org.aldousdev.dockflowbackend.auth.service.AuthService;
import org.aldousdev.dockflowbackend.auth.service.impls.AuthServiceImpl;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;


@Aspect
@Component
@RequiredArgsConstructor
public class RoleLevelAspect {
    private final JWTService jwtService;

    @Before("@annotation(requiresRoleLevel)")
    public void checkRoleLevel(RequiresRoleLevel requiresRoleLevel) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if(!(authentication instanceof JwtAuthenticationToken jwtAuth)){
            throw new SecurityException("Unauthorized");
        }

//        String token = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String token = jwtAuth.getToken();
        if(token == null){
            throw new SecurityException("Token is null");
        }

        Integer roleLevel = jwtService.extractCompanyRoleLevel(token);

        if(roleLevel ==null){
            throw new SecurityException("Role level is null");
        }

        if(roleLevel < requiresRoleLevel.value()){
            throw new SecurityException("Role level is lower than role level");
        }

    }
}
