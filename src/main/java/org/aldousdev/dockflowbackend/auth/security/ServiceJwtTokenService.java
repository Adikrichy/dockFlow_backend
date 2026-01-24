package org.aldousdev.dockflowbackend.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
@RequiredArgsConstructor
@Slf4j
public class ServiceJwtTokenService {

    private static final String TOKEN_TYPE = "service";
    private static final String CLAIM_TYPE = "typ";

    private final ServiceJwtProperties properties;

    public String generateToken() {
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(properties.getTtlSeconds());

        return Jwts.builder()
                .issuer(properties.getIssuer())
                .audience().add(properties.getAudience()).and()
                .subject("service:dockflow-ai")
                .claim(CLAIM_TYPE, TOKEN_TYPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public ServiceJwtValidationResult validateToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .requireIssuer(properties.getIssuer())
                    .requireAudience(properties.getAudience())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String tokenType = claims.get(CLAIM_TYPE, String.class);
            if (!TOKEN_TYPE.equals(tokenType)) {
                log.warn("Service JWT validation failed: invalid token type '{}', expected '{}'", tokenType, TOKEN_TYPE);
                return ServiceJwtValidationResult.failure("Invalid token type: expected 'service'");
            }

            String subject = claims.getSubject();
            if (!properties.getAllowedServices().contains(subject)) {
                log.warn("Service JWT validation failed: service '{}' not in allowed list", subject);
                return ServiceJwtValidationResult.failure("Service not authorized: " + subject);
            }

            log.debug("Service JWT validated successfully for subject: {}", subject);
            return ServiceJwtValidationResult.success(subject);

        } catch (ExpiredJwtException e) {
            log.warn("Service JWT validation failed: token expired at {}", e.getClaims().getExpiration());
            return ServiceJwtValidationResult.failure("Token expired");
        } catch (JwtException e) {
            log.warn("Service JWT validation failed: {}", e.getMessage());
            return ServiceJwtValidationResult.failure("Invalid token: " + e.getMessage());
        } catch (Exception e) {
            log.error("Service JWT validation failed with unexpected error", e);
            return ServiceJwtValidationResult.failure("Token validation error");
        }
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public record ServiceJwtValidationResult(boolean valid, String subject, String error) {
        public static ServiceJwtValidationResult success(String subject) {
            return new ServiceJwtValidationResult(true, subject, null);
        }

        public static ServiceJwtValidationResult failure(String error) {
            return new ServiceJwtValidationResult(false, null, error);
        }
    }
}
