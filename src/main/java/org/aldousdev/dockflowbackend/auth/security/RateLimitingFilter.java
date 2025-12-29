package org.aldousdev.dockflowbackend.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.auth.service.SecurityAuditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Простой rate limiting фильтр для защиты от brute force атак
 * В продакшене рекомендуется заменить на Redis-based решение
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(1)
public class RateLimitingFilter extends OncePerRequestFilter {

    private final SecurityAuditService securityAuditService;

    // Хранение счетчиков запросов по IP адресу
    private final ConcurrentHashMap<String, ClientRequestInfo> requestCounts = new ConcurrentHashMap<>();

    // Лимиты
    private static final int MAX_REQUESTS_PER_MINUTE = 10; // Для auth endpoints
    private static final int MAX_REQUESTS_PER_HOUR = 100; // Для всех остальных
    private static final long CLEANUP_INTERVAL = 60000; // 1 минута

    @Value("${rate.limit.enable:true}")
    private boolean rateLimitEnabled;

    private long lastCleanup = System.currentTimeMillis();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if(!rateLimitEnabled) {
            filterChain.doFilter(request,response);
            return;
        }


        String clientIP = getClientIP(request);
        String requestURI = request.getRequestURI();

        // Очистка устаревших записей
        cleanupExpiredEntries();

        ClientRequestInfo clientInfo = requestCounts.computeIfAbsent(clientIP,
            k -> new ClientRequestInfo());

        // Проверка лимитов
        boolean isAuthEndpoint = requestURI.contains("/api/auth/") ||
                                requestURI.contains("/api/register");

        long currentTime = System.currentTimeMillis();
        int maxRequests = isAuthEndpoint ? MAX_REQUESTS_PER_MINUTE : MAX_REQUESTS_PER_HOUR;
        long timeWindow = isAuthEndpoint ? 60000 : 3600000; // 1 min or 1 hour

        // Проверка превышения лимита
        if (clientInfo.isRateLimitExceeded(maxRequests, timeWindow, currentTime)) {
            log.warn("Rate limit exceeded for IP: {} on endpoint: {}", clientIP, requestURI);

            // Логируем security событие
            securityAuditService.logRateLimitExceeded(clientIP, request.getHeader("User-Agent"), requestURI);

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("""
                {
                    "error": "Too Many Requests",
                    "message": "Rate limit exceeded. Please try again later.",
                    "retryAfter": 60
                }
                """);
            return;
        }

        // Увеличение счетчика
        clientInfo.incrementRequestCount(currentTime);

        filterChain.doFilter(request, response);
    }

    private String getClientIP(HttpServletRequest request) {
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

    private void cleanupExpiredEntries() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCleanup > CLEANUP_INTERVAL) {
            requestCounts.entrySet().removeIf(entry ->
                currentTime - entry.getValue().getLastRequestTime() > 3600000); // 1 hour
            lastCleanup = currentTime;
        }
    }

    /**
     * Информация о запросах клиента
     */
    private static class ClientRequestInfo {
        private final AtomicLong requestCount = new AtomicLong(0);
        private volatile long lastRequestTime = System.currentTimeMillis();
        private volatile long windowStartTime = System.currentTimeMillis();

        public void incrementRequestCount(long currentTime) {
            requestCount.incrementAndGet();
            lastRequestTime = currentTime;
        }

        public boolean isRateLimitExceeded(int maxRequests, long timeWindow, long currentTime) {
            // Сброс счетчика при новом временном окне
            if (currentTime - windowStartTime > timeWindow) {
                requestCount.set(1);
                windowStartTime = currentTime;
                lastRequestTime = currentTime;
                return false;
            }

            return requestCount.get() >= maxRequests;
        }

        public long getLastRequestTime() {
            return lastRequestTime;
        }
    }
}
