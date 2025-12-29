
package org.aldousdev.dockflowbackend.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class JWTService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${jwt.refresh-expiration:604800000}") // 7 дней по умолчанию
    private long refreshExpiration;

    // Генерация ключа для подписи
    private Key getSignInKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // Генерация JWT токена с userId и userType
    public String generateToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .setSubject(user.getEmail())
                .claim("userId", user.getId())
                .claim("userType", user.getUserType().name())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSignInKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    public String generateCompanyToken(User user, Map<String,Object> extraClaims){
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .setSubject(user.getEmail())
                .claim("userId", user.getId())
                .claim("userType", user.getUserType().name())
                .addClaims(extraClaims)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSignInKey(),SignatureAlgorithm.HS512)
                .compact();
    }

    // Генерация refresh token
    public String generateRefreshToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshExpiration);

        return Jwts.builder()
                .setSubject(user.getEmail())
                .claim("tokenType", "refresh")
                .claim("userId", user.getId())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSignInKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    // Проверка валидности refresh token
    public boolean isRefreshTokenValid(String refreshToken, User user) {
        try {
            boolean isValid = isTokenValid(refreshToken) &&
                            extractEmail(refreshToken).equals(user.getEmail()) &&
                            "refresh".equals(extractTokenType(refreshToken));

            // Дополнительная проверка на соответствие сохраненному refresh token
            return isValid && refreshToken.equals(user.getRefreshToken()) &&
                   user.getRefreshTokenExpiry() != null &&
                   user.getRefreshTokenExpiry().isAfter(java.time.LocalDateTime.now());

        } catch (Exception e) {
            return false;
        }
    }

    // Извлечение типа токена (access/refresh)
    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get("tokenType", String.class));
    }

    // Извлечение даты истечения refresh token
    public java.time.LocalDateTime getRefreshTokenExpiry(String refreshToken) {
        Date expiry = extractClaim(refreshToken, Claims::getExpiration);
        return expiry.toInstant()
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDateTime();
    }

    // Проверка валидности токена (подпись + срок)
    public boolean isTokenValid(String token) {
        try {
            Jwts.parser()
                    .setSigningKey(getSignInKey())
                    .build()
                    .parseClaimsJws(token);
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    // Проверка валидности токена для конкретного пользователя
    public boolean isTokenValid(String token, User user) {
        return isTokenValid(token) && extractEmail(token).equals(user.getEmail());
    }

    // Извлечение email из токена
    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    // Извлечение userType из токена
    public String extractUserType(String token) {
        return extractClaim(token, claims -> claims.get("userType", String.class));
    }

    // Извлечение userId из токена
    public Long extractUserId(String token) {
        return extractClaim(token, claims -> ((Number) claims.get("userId")).longValue());
    }

    // Проверка срока действия токена
    public boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    // Универсальный метод для извлечения любого поля
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    // Извлечение всех claims
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String extractCompanyRole(String token){
        return extractClaim(token, claims -> claims.get("companyRole", String.class));
    }

    public Integer extractCompanyRoleLevel(String token){
        return extractClaim(token, claims -> claims.get("companyRoleLevel", Integer.class));
    }

    public Long extractCompanyId(String token){
        return extractClaim(token, claims -> claims.get("companyId", Long.class));
    }

}

