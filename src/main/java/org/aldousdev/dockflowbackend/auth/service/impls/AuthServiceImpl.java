package org.aldousdev.dockflowbackend.auth.service.impls;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.auth.dto.request.LoginRequest;
import org.aldousdev.dockflowbackend.auth.dto.response.LoginResponse;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.auth.enums.Status;
import org.aldousdev.dockflowbackend.auth.exceptions.UserNotActiveException;
import org.aldousdev.dockflowbackend.auth.entity.PasswordResetToken;
import org.aldousdev.dockflowbackend.auth.repository.PasswordResetTokenRepository;
import org.aldousdev.dockflowbackend.auth.repository.UserRepository;
import org.aldousdev.dockflowbackend.auth.security.JWTService;
import org.aldousdev.dockflowbackend.auth.mapper.AuthMapper;
import org.aldousdev.dockflowbackend.auth.service.AuthService;
import org.aldousdev.dockflowbackend.auth.service.SecurityAuditService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JWTService jwtService;
    private final AuthMapper authMapper;
    private final SecurityAuditService securityAuditService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailServiceImpl emailService;

    @Override
    public LoginResponse login(LoginRequest request, HttpServletResponse response) {
        String clientIP = "unknown";
        String userAgent = "unknown";

        log.info("Login attempt for email: {} from IP: {}", request.getEmail(), clientIP);

        User user = userRepository.findByEmailWithMemberships(request.getEmail())
                .orElseThrow(() -> {
                    securityAuditService.logLoginFailed(request.getEmail(), clientIP, userAgent, "User not found");
                    return new RuntimeException("User not found");
                });

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            securityAuditService.logLoginFailed(request.getEmail(), clientIP, userAgent, "Invalid password");
            throw new RuntimeException("Wrong password");
        }

        if (user.getStatus() != Status.ACTIVE) {
            securityAuditService.logLoginFailed(request.getEmail(), clientIP, userAgent, "User is inactive");
            throw new UserNotActiveException("User is locked");
        }

        // Генерация токенов
        String accessToken;
        if (user.getMemberships() != null && !user.getMemberships().isEmpty()) {
            var membership = user.getMemberships().get(0);
            java.util.Map<String, Object> claims = new java.util.HashMap<>();
            claims.put("companyRole", membership.getRole().getName());
            claims.put("companyId", membership.getCompany().getId());
            claims.put("companyRoleLevel", membership.getRole().getLevel());
            accessToken = jwtService.generateCompanyToken(user, claims);
        } else {
            accessToken = jwtService.generateToken(user);
        }

        String refreshToken = jwtService.generateRefreshToken(user);
        LocalDateTime refreshExpiry = jwtService.getRefreshTokenExpiry(refreshToken);

        // Сохраняем refresh token в БД
        user.setRefreshToken(refreshToken);
        user.setRefreshTokenExpiry(refreshExpiry);
        userRepository.save(user);

        // Устанавливаем две HttpOnly cookie
        setCookie(response, "accessToken", accessToken, 3600);            // 1 час
        setCookie(response, "refreshToken", refreshToken, 7 * 24 * 3600); // 7 дней

        securityAuditService.logLoginSuccess(user, clientIP, userAgent);
        log.info("User successfully logged in: {}", request.getEmail());

        LoginResponse loginResponse = authMapper.toLoginResponse(user);
        populateCompanyRole(user, loginResponse);
        return loginResponse;
    }

    @Override
    public void logout(HttpServletResponse response) {
        clearCookies(response);

        try {
            User currentUser = getCurrentUser();
            currentUser.setRefreshToken(null);
            currentUser.setRefreshTokenExpiry(null);
            userRepository.save(currentUser);
            securityAuditService.logLogout(currentUser, "unknown", "unknown");
        } catch (Exception e) {
            // Если пользователь уже не авторизован — просто игнорируем
            log.debug("No authenticated user during logout");
        }

        log.info("User logged out successfully");
    }

    @Override
    public User getCurrentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Authentication not found");
        }

        if (authentication.getPrincipal() instanceof User user) {
            return user;
        }

        throw new RuntimeException("Principal is not User instance");
    }

    // === НОВЫЙ МЕТОД: refresh из cookie ===
    public LoginResponse refreshTokenFromCookie(HttpServletRequest request, HttpServletResponse response) {
        log.info("Attempting token refresh from cookie");

        String refreshToken = getCookieValue(request, "refreshToken")
                .orElseThrow(() -> {
                    securityAuditService.logTokenRefreshFailed("unknown", "unknown", "No refresh token cookie");
                    return new RuntimeException("No refresh token");
                });

        User user = userRepository.findByRefreshTokenWithMemberships(refreshToken)
                .orElseThrow(() -> {
                    securityAuditService.logTokenRefreshFailed("unknown", "unknown", "Refresh token not found in DB");
                    return new RuntimeException("Invalid refresh token");
                });

        if (!jwtService.isRefreshTokenValid(refreshToken, user)) {
            user.setRefreshToken(null);
            user.setRefreshTokenExpiry(null);
            userRepository.save(user);
            clearCookies(response);

            securityAuditService.logTokenRefreshFailed("unknown", "unknown", "Invalid or expired refresh token");
            throw new RuntimeException("Invalid or expired refresh token");
        }

        // Новый access token
        String newAccessToken;
        if (user.getMemberships() != null && !user.getMemberships().isEmpty()) {
            var membership = user.getMemberships().get(0);
            java.util.Map<String, Object> claims = new java.util.HashMap<>();
            claims.put("companyRole", membership.getRole().getName());
            claims.put("companyId", membership.getCompany().getId());
            claims.put("companyRoleLevel", membership.getRole().getLevel());
            newAccessToken = jwtService.generateCompanyToken(user, claims);
        } else {
            newAccessToken = jwtService.generateToken(user);
        }

        // Ротация refresh token (безопасность)
        String newRefreshToken = jwtService.generateRefreshToken(user);
        LocalDateTime newRefreshExpiry = jwtService.getRefreshTokenExpiry(newRefreshToken);

        user.setRefreshToken(newRefreshToken);
        user.setRefreshTokenExpiry(newRefreshExpiry);
        userRepository.save(user);

        // Обновляем cookie
        setCookie(response, "accessToken", newAccessToken, 3600);
        setCookie(response, "refreshToken", newRefreshToken, 7 * 24 * 3600);

        securityAuditService.logTokenRefresh(user, "unknown", "unknown");
        log.info("Token refreshed successfully for user: {}", user.getEmail());

        LoginResponse loginResponse = authMapper.toLoginResponse(user);
        populateCompanyRole(user, loginResponse);
        return loginResponse;
    }

    // === УДАЛИ СТАРЫЙ МЕТОД refreshToken(String, HttpServletResponse) ===
    // Он больше не нужен — удали его полностью, чтобы не было дублирования

    // === Вспомогательные методы ===
    private void setCookie(HttpServletResponse response, String name, String value, int maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // false на localhost, в проде — true (или через @Value)
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        response.addCookie(cookie);
    }

    private java.util.Optional<String> getCookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return java.util.Optional.empty();
        return Arrays.stream(request.getCookies())
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }

    private void clearCookies(HttpServletResponse response) {
        setCookie(response, "accessToken", "", 0);
        setCookie(response, "refreshToken", "", 0);
    }

    private String getClientIP(jakarta.servlet.http.HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIP = request.getHeader("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty()) {
            return xRealIP;
        }
        return request.getRemoteAddr();
    }
    @Override
    @Transactional
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        // Delete existing tokens for this user
        passwordResetTokenRepository.deleteByUser(user);

        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(token)
                .user(user)
                .expiryDate(LocalDateTime.now().plusHours(1))
                .build();

        passwordResetTokenRepository.save(resetToken);

        String resetLink = "http://localhost:5173/reset-password?token=" + token;
        emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
        
        log.info("Password reset link sent to: {}", email);
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid token"));

        if (resetToken.isExpired()) {
            passwordResetTokenRepository.delete(resetToken);
            throw new RuntimeException("Token expired");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        passwordResetTokenRepository.delete(resetToken);
        
        log.info("Password reset successful for user: {}", user.getEmail());
    }

    private void populateCompanyRole(User user, LoginResponse response) {
        if (user.getMemberships() != null && !user.getMemberships().isEmpty()) {
            String role = user.getMemberships().get(0).getRole().getName();
            response.getUser().setCompanyRole(role);
        } else {
             response.getUser().setCompanyRole("Member");
        }
    }
}