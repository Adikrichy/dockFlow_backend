
package org.aldousdev.dockflowbackend.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Slf4j
public class JWTService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${jwt.refresh-expiration:604800000}") // 7 days by default
    private long refreshExpiration;

    // Signature key generation
    private Key getSignInKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // JWT token generation with userId and userType
    public String generateToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getId())
                .claim("userType", user.getUserType().name())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSignInKey())
                .compact();
    }

    public String generateCompanyToken(User user, Map<String,Object> extraClaims){
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getId())
                .claim("userType", user.getUserType().name())
                .claims(extraClaims)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSignInKey())
                .compact();
    }

    // Refresh token generation
    public String generateRefreshToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshExpiration);

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("tokenType", "refresh")
                .claim("userId", user.getId())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSignInKey())
                .compact();
    }

    // Refresh token validity check
    public boolean isRefreshTokenValid(String refreshToken, User user) {
        try {
            boolean isValid = isTokenValid(refreshToken) &&
                            extractEmail(refreshToken).equals(user.getEmail()) &&
                            "refresh".equals(extractTokenType(refreshToken));

            // Additional check for match with stored refresh token
            return isValid && refreshToken.equals(user.getRefreshToken()) &&
                   user.getRefreshTokenExpiry() != null &&
                   user.getRefreshTokenExpiry().isAfter(java.time.LocalDateTime.now());

        } catch (Exception e) {
            return false;
        }
    }

    // Extract token type (access/refresh)
    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get("tokenType", String.class));
    }

    // Extract refresh token expiration date
    public java.time.LocalDateTime getRefreshTokenExpiry(String refreshToken) {
        Date expiry = extractClaim(refreshToken, Claims::getExpiration);
        return expiry.toInstant()
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDateTime();
    }

    // Token validity check (signature + expiration)
    public boolean isTokenValid(String token) {
        try {
            Jwts.parser()
                    .verifyWith((javax.crypto.SecretKey) getSignInKey())
                    .build()
                    .parseSignedClaims(token);
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    // Token validity check for a specific user
    public boolean isTokenValid(String token, User user) {
        return isTokenValid(token) && extractEmail(token).equals(user.getEmail());
    }

    // Extract email from token
    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    // Extract userType from token
    public String extractUserType(String token) {
        return extractClaim(token, claims -> claims.get("userType", String.class));
    }

    // Extract userId from token
    public Long extractUserId(String token) {
        return extractClaim(token, claims -> ((Number) claims.get("userId")).longValue());
    }

    // Token expiration check
    public boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    // Universal method for extracting any field
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    // Extract all claims
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractCompanyRole(String token){
        return extractClaim(token, claims -> claims.get("companyRole", String.class));
    }

    public Integer extractCompanyRoleLevel(String token){
        log.info("==== Extracting company Role Level ====");
        Integer result = extractClaim(token, claims -> {
            Object role = claims.get("companyRoleLevel");
            log.info("Raw claim value {}", role);
            log.info("Claim type {}", role != null ? role.getClass().getName(): "null");

            if(role == null){
                log.warn("CompanyRoleLevel is null");
                return null;
            }

            if(role instanceof Integer){
                log.info("CompanyRoleLevel is Integer");
                return (Integer)role;
            }

            if(role instanceof Number){
                Integer converted = ((Number) role).intValue();
                log.info("Converted Number to Integer {}", converted);
                return converted;
            }

            if(role instanceof String){
                try{
                    Integer parser = Integer.parseInt((String) role);
                    log.info("Converted String to Integer {}", parser);
                    return parser;
                }
                catch(NumberFormatException e){
                   log.error("Can not Parsing String {}", role);
                   return null;
                }
            }

            log.error("Unexpected type: {}", role.getClass().getName());
            return null;
        });

        log.info("Final result: {}", result);
        log.info("=== EXTRACTION COMPLETE ===");
        return result;
    }

    public Long extractCompanyId(String token){
        return extractClaim(token, claims -> {
            Object id = claims.get("companyId");
            if (id == null) return null;
            if (id instanceof Number) return ((Number) id).longValue();
            return null;
        });
    }

    public Long extractCompanyIdFromAuth(Authentication authentication){
        if(authentication == null || !authentication.isAuthenticated()){
            return null;
        }

        if(authentication.getPrincipal() instanceof String token){
            return extractCompanyId(token);
        }
        return null;
    }

}

