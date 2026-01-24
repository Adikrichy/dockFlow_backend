package org.aldousdev.dockflowbackend.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ServiceJwtFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String INTERNAL_PATH_PREFIX = "/api/internal/";

    private final ServiceJwtTokenService serviceJwtTokenService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith(INTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("Service JWT missing or malformed for internal endpoint: {}", request.getRequestURI());
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Authorization header required for internal endpoints\"}");
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        ServiceJwtTokenService.ServiceJwtValidationResult result = serviceJwtTokenService.validateToken(token);

        if (!result.valid()) {
            log.warn("Service JWT validation failed for {}: {}", request.getRequestURI(), result.error());
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"" + result.error() + "\"}");
            return;
        }

        log.info("Service JWT authenticated for internal endpoint: {} by {}", request.getRequestURI(), result.subject());

        ServiceJwtAuthentication authentication = new ServiceJwtAuthentication(result.subject());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    public static class ServiceJwtAuthentication extends AbstractAuthenticationToken {
        private final String serviceSubject;

        public ServiceJwtAuthentication(String serviceSubject) {
            super(List.of(new SimpleGrantedAuthority("ROLE_SERVICE")));
            this.serviceSubject = serviceSubject;
            setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            return null;
        }

        @Override
        public Object getPrincipal() {
            return serviceSubject;
        }

        public String getServiceSubject() {
            return serviceSubject;
        }
    }
}
