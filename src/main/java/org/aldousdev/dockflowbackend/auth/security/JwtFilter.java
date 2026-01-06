package org.aldousdev.dockflowbackend.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.aldousdev.dockflowbackend.auth.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JWTService jwtService;
    private final UserRepository userRepository;

    // Список public путей — должен совпадать с permitAll() в SecurityConfig
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/register",
            "/api/auth/verify-email",
            "/api/auth/login",
            "/api/auth/resend-verification-code",
            "/api/company/list",
            "/api/company/search"
    );

    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/swagger-ui/",
            "/v3/api-docs",
            "/api-docs"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        if (isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = extractTokenFromCookies(request);

        if (token == null) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return;
        }
        if (!jwtService.isTokenValid(token)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return;
        }
        String email = jwtService.extractEmail(token);

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            userRepository.findByEmailWithMemberships(email).ifPresent(user -> {
                if (jwtService.isTokenValid(token, user)) {
                    JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                            user,
                            token,
                            user.getAuthorities()
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            });
        }

        // В любом случае продолжаем цепочку (даже если пользователь не найден — просто без аутентификации)
        filterChain.doFilter(request, response);
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.contains(path) ||
                PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private String extractTokenFromCookies(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        // Пытаемся сначала найти токен с контекстом компании
        String companyToken = Arrays.stream(cookies)
                .filter(c -> "jwtWithCompany".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);

        if (companyToken != null && jwtService.isTokenValid(companyToken)) {
            return companyToken;
        }

        // Если нет токена компании или он невалиден, берем обычный access token
        return Arrays.stream(cookies)
                .filter(cookie ->
                        "accessToken".equals(cookie.getName()) ||
                                "JWT".equals(cookie.getName())
                )
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}