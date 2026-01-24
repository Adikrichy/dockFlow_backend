package org.aldousdev.dockflowbackend.document_edit.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.document_edit.validator.OnlyOfficeJwtValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OnlyOfficeJwtFilter extends OncePerRequestFilter {
    private final OnlyOfficeJwtValidator jwtValidator;
    private final ObjectMapper objectMapper;


    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getServletPath();
        log.info("[OnlyOfficeJwtFilter] Обработка запроса: {} {}", request.getMethod(), path);

        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
        String token = extractToken(cachedRequest);

        if (token == null) {
            log.error("[OnlyOfficeJwtFilter] Токен не найден");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": 1}");
            return;
        }

        if (!jwtValidator.validateToken(token)) {
            log.error("[OnlyOfficeJwtFilter] Токен невалиден");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": 1}");
            return;
        }

        log.info("[OnlyOfficeJwtFilter] Токен валиден");



        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                "onlyoffice",
                null,
                Collections.singleton(new SimpleGrantedAuthority("ROLE_ONLYOFFICE")));
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        filterChain.doFilter(cachedRequest, response);
    }

    private String extractToken(CachedBodyHttpServletRequest request) {
        String path = request.getServletPath();
        String method = request.getMethod();
        log.debug("[OnlyOfficeJwtFilter] Extracting token for path: {} method: {}", path, method);

        // 1. Try Headers first (common for both GET and POST)
        String[] headerNames = {
                "Authorization", "authorization",
                "Token", "token",
                "X-Token", "x-token",
                "X-Access-Token", "x-access-token",
                "X-Authorization", "x-authorization"
        };

        for (String headerName : headerNames) {
            String headerValue = request.getHeader(headerName);
            if (headerValue != null && !headerValue.isBlank()) {
                log.info("[OnlyOfficeJwtFilter] Found token in header {}: {}...",
                        headerName, headerValue.length() > 30 ? headerValue.substring(0, 30) : headerValue);

                if (headerName.toLowerCase().contains("authorization") &&
                        headerValue.startsWith("Bearer ")) {
                    return headerValue.substring(7);
                }
                return headerValue;
            }
        }

        // 2. Try Body fallback (common for POST callbacks)
        if ("POST".equalsIgnoreCase(method)) {
            try {
                String body = request.getBody();
                if (body != null && !body.isEmpty()) {
                    Map<String, Object> json = objectMapper.readValue(body, Map.class);
                    if (json.containsKey("token")) {
                        String token = String.valueOf(json.get("token"));
                        log.info("[OnlyOfficeJwtFilter] Found token in body 'token' field: {}...",
                                token.length() > 30 ? token.substring(0, 30) : token);
                        return token;
                    }
                }
            } catch (Exception e) {
                log.error("[OnlyOfficeJwtFilter] Error reading body for token extraction: {}", e.getMessage());
            }
        }

        log.error("[OnlyOfficeJwtFilter] No token found for {} {}", method, path);
        return null;
    }


    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        boolean isOnlyOfficePath = path.startsWith("/api/document-edit/file/") ||
                path.startsWith("/api/document-edit/onlyoffice/callback/");
        return !isOnlyOfficePath;
    }
}