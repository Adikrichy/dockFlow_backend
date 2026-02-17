package org.aldousdev.dockflowbackend.document_edit.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.document_edit.entity.WopiToken;
import org.aldousdev.dockflowbackend.document_edit.repository.WopiTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WopiTokenService {

    private final WopiTokenRepository tokenRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String createToken(String sessionKey, Long userId, int ttlMinutes) {
        String token = UUID.randomUUID().toString().replace("-", "");
        WopiToken wopiToken = WopiToken.builder()
                .token(token)
                .sessionKey(sessionKey)
                .userId(userId)
                .expiresAt(LocalDateTime.now().plusMinutes(ttlMinutes))
                .build();
        
        tokenRepository.save(wopiToken);
        log.debug("Created WOPI token: {} for session: {}", token, sessionKey);
        return token;
    }

    @Transactional(readOnly = true)
    public Optional<WopiToken> validateToken(String token) {
        return tokenRepository.findByToken(token)
                .filter(t -> t.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    @Transactional
    public void deleteToken(String token) {
        tokenRepository.deleteById(token);
    }
}
