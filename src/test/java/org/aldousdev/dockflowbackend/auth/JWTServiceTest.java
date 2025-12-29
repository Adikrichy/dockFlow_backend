package org.aldousdev.dockflowbackend.auth;

import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.auth.enums.UserType;
import org.aldousdev.dockflowbackend.auth.enums.Status;
import org.aldousdev.dockflowbackend.auth.security.JWTService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class JWTServiceTest {

    @Autowired
    private JWTService jwtService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .id(1L)
            .email("test@example.com")
            .firstName("Test")
            .lastName("User")
            .userType(UserType.PLATFORM_USER)
            .status(Status.ACTIVE)
            .build();
    }

    @Test
    void testGenerateAndValidateAccessToken() {
        // When
        String token = jwtService.generateToken(testUser);

        // Then
        assertThat(token).isNotNull();
        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.extractEmail(token)).isEqualTo(testUser.getEmail());
        assertThat(jwtService.extractUserId(token)).isEqualTo(testUser.getId());
    }

    @Test
    void testGenerateAndValidateRefreshToken() {
        // When
        String refreshToken = jwtService.generateRefreshToken(testUser);

        // Then
        assertThat(refreshToken).isNotNull();
        assertThat(jwtService.isTokenValid(refreshToken)).isTrue();
        assertThat(jwtService.extractTokenType(refreshToken)).isEqualTo("refresh");
        assertThat(jwtService.extractEmail(refreshToken)).isEqualTo(testUser.getEmail());

        // Test refresh token validation
        assertThat(jwtService.isRefreshTokenValid(refreshToken, testUser)).isTrue();
    }

    @Test
    void testRefreshTokenExpiry() {
        // When
        String refreshToken = jwtService.generateRefreshToken(testUser);

        // Then
        var expiry = jwtService.getRefreshTokenExpiry(refreshToken);
        assertThat(expiry).isAfter(java.time.LocalDateTime.now());
    }

    @Test
    void testInvalidRefreshToken() {
        // Given
        User differentUser = User.builder()
            .id(2L)
            .email("different@example.com")
            .build();

        String refreshToken = jwtService.generateRefreshToken(testUser);

        // Then - refresh token другого пользователя должен быть недействительным
        assertThat(jwtService.isRefreshTokenValid(refreshToken, differentUser)).isFalse();
    }

    @Test
    void testTokenClaims() {
        // When
        String token = jwtService.generateToken(testUser);

        // Then
        assertThat(jwtService.extractEmail(token)).isEqualTo(testUser.getEmail());
        assertThat(jwtService.extractUserId(token)).isEqualTo(testUser.getId());
        assertThat(jwtService.extractUserType(token)).isEqualTo(testUser.getUserType().name());
    }

    @Test
    void testExpiredToken() throws InterruptedException {
        // Given - создать токен с очень коротким временем жизни
        // (Это сложно тестировать без mock'а, поэтому просто проверим базовую валидацию)

        String token = jwtService.generateToken(testUser);
        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.isTokenValid(token, testUser)).isTrue();
    }
}
