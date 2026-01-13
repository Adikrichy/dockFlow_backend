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
        log.info("[OnlyOfficeJwtFilter] Extracting token for path: {} method: {}", path, method);

        // 1. Для callback запросов (POST) - токен в теле JSON
        if (path.contains("/onlyoffice/callback/") && "POST".equalsIgnoreCase(method)) {
            try {
                String body = request.getBody();
                log.info("[OnlyOfficeJwtFilter] Callback body (first 500 chars): {}",
                        body != null && body.length() > 500 ? body.substring(0, 500) + "..." : body);

                if (body != null && !body.isEmpty()) {
                    Map<String, Object> json = objectMapper.readValue(body, Map.class);

                    // Ищем токен в разных возможных местах
                    if (json.containsKey("token")) {
                        String token = String.valueOf(json.get("token"));
                        log.info("[OnlyOfficeJwtFilter] Found token in body.token: {}...",
                                token.length() > 30 ? token.substring(0, 30) : token);
                        return token;
                    }

                    // Если токена нет в корне, возможно он в другом месте
                    log.warn("[OnlyOfficeJwtFilter] No 'token' field in callback body. Keys: {}", json.keySet());

                    // Пробуем найти в других местах (на случай разных версий OnlyOffice)
                    for (String key : json.keySet()) {
                        log.debug("[OnlyOfficeJwtFilter] Body key: {} = {}", key, json.get(key));
                    }
                }
            } catch (Exception e) {
                log.error("[OnlyOfficeJwtFilter] Error reading callback body: {}", e.getMessage(), e);
            }
            return null;
        }

        // 2. Для файловых запросов (GET) - токен в заголовках
        if (path.contains("/api/document-edit/file/") && "GET".equalsIgnoreCase(method)) {
            // Пробуем все возможные заголовки
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
                    log.info("[OnlyOfficeJwtFilter] Found header {}: {}...",
                            headerName, headerValue.length() > 30 ? headerValue.substring(0, 30) : headerValue);

                    // Если это Authorization header с Bearer, извлекаем токен
                    if (headerName.toLowerCase().contains("authorization") &&
                            headerValue.startsWith("Bearer ")) {
                        return headerValue.substring(7);
                    }
                    return headerValue;
                }
            }

            log.error("[OnlyOfficeJwtFilter] No token found in headers for file request. All headers:");
            Enumeration<String> headerNamesEnum = request.getHeaderNames();
            while (headerNamesEnum.hasMoreElements()) {
                String name = headerNamesEnum.nextElement();
                log.error("[OnlyOfficeJwtFilter]   {}: {}", name, request.getHeader(name));
            }
            return null;
        }

        log.error("[OnlyOfficeJwtFilter] Unknown request type: {} {}", method, path);
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