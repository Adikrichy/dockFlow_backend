package org.aldousdev.dockflowbackend.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.auth.entity.SecurityAuditLog;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.auth.repository.SecurityAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис для логирования событий безопасности
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityAuditService {

    private final SecurityAuditRepository securityAuditRepository;

    public enum SecurityEvent {
        LOGIN_SUCCESS,
        LOGIN_FAILED,
        LOGOUT,
        TOKEN_REFRESH,
        TOKEN_REFRESH_FAILED,
        PASSWORD_CHANGE,
        SUSPICIOUS_ACTIVITY,
        RATE_LIMIT_EXCEEDED
    }

    /**
     * Логирование успешного входа
     */
    @Transactional
    public void logLoginSuccess(User user, String ipAddress, String userAgent) {
        logSecurityEvent(SecurityEvent.LOGIN_SUCCESS, user, ipAddress, userAgent,
                        "User logged in successfully");
    }

    /**
     * Логирование неудачной попытки входа
     */
    @Transactional
    public void logLoginFailed(String email, String ipAddress, String userAgent, String reason) {
        logSecurityEvent(SecurityEvent.LOGIN_FAILED, null, ipAddress, userAgent,
                        String.format("Login failed for email '%s': %s", email, reason));
    }

    /**
     * Логирование выхода из системы
     */
    @Transactional
    public void logLogout(User user, String ipAddress, String userAgent) {
        logSecurityEvent(SecurityEvent.LOGOUT, user, ipAddress, userAgent,
                        "User logged out");
    }

    /**
     * Логирование обновления токена
     */
    @Transactional
    public void logTokenRefresh(User user, String ipAddress, String userAgent) {
        logSecurityEvent(SecurityEvent.TOKEN_REFRESH, user, ipAddress, userAgent,
                        "Access token refreshed successfully");
    }

    /**
     * Логирование неудачного обновления токена
     */
    @Transactional
    public void logTokenRefreshFailed(String ipAddress, String userAgent, String reason) {
        logSecurityEvent(SecurityEvent.TOKEN_REFRESH_FAILED, null, ipAddress, userAgent,
                        "Token refresh failed: " + reason);
    }

    /**
     * Логирование превышения rate limit
     */
    @Transactional
    public void logRateLimitExceeded(String ipAddress, String userAgent, String endpoint) {
        logSecurityEvent(SecurityEvent.RATE_LIMIT_EXCEEDED, null, ipAddress, userAgent,
                        "Rate limit exceeded for endpoint: " + endpoint);
    }

    /**
     * Логирование подозрительной активности
     */
    @Transactional
    public void logSuspiciousActivity(User user, String ipAddress, String userAgent, String details) {
        logSecurityEvent(SecurityEvent.SUSPICIOUS_ACTIVITY, user, ipAddress, userAgent, details);
    }

    private void logSecurityEvent(SecurityEvent event, User user, String ipAddress,
                                 String userAgent, String details) {
        try {
            SecurityAuditLog auditLog = SecurityAuditLog.builder()
                    .eventType(event.name())
                    .user(user)
                    .userEmail(user != null ? user.getEmail() : null)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .details(details)
                    .timestamp(java.time.LocalDateTime.now())
                    .build();

            securityAuditRepository.save(auditLog);

            log.info("Security event logged: {} - {} - {}",
                    event.name(),
                    user != null ? user.getEmail() : "unknown",
                    ipAddress);

        } catch (Exception e) {
            log.error("Failed to log security event: {}", e.getMessage());
            // Не выбрасываем исключение, чтобы не прерывать основной поток
        }
    }
}
