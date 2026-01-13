package org.aldousdev.dockflowbackend.document_edit.validator;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class OnlyOfficeJwtValidator {
    @Value("${onlyoffice.jwt.secret}")
    private String jwtSecret;

    public boolean validateToken(String token){
        if(token.startsWith("Bearer ")){
            token = token.substring(7);
        }
        try{
            Jws<Claims> jws = Jwts.parser()
                    .setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                    .build()
                    .parseClaimsJws(token);
            log.debug("[OnlyOfficeJwtValidator] Token valid. Claims: {}", jws.getBody());
            return true;
        }
        catch (JwtException e){
            log.warn("[OnlyOfficeJwtValidator] Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    public Claims parseClaims(String token){
        return Jwts.parser()
                .setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}

