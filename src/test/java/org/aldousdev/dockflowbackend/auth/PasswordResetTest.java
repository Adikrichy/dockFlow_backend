package org.aldousdev.dockflowbackend.auth;

import org.aldousdev.dockflowbackend.auth.entity.PasswordResetToken;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.auth.repository.PasswordResetTokenRepository;
import org.aldousdev.dockflowbackend.auth.repository.UserRepository;
import org.aldousdev.dockflowbackend.auth.service.impls.AuthServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class PasswordResetTest {

    @Autowired
    private AuthServiceImpl authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Test
    public void testForgotPasswordCreatesToken() {
        // Prepare user
        User user = User.builder()
                .email("test@example.com")
                .password("password123456")
                .firstName("Test")
                .lastName("User")
                .status(org.aldousdev.dockflowbackend.auth.enums.Status.ACTIVE)
                .userType(org.aldousdev.dockflowbackend.auth.enums.UserType.USER)
                .build();
        userRepository.save(user);

        // Request password reset
        authService.forgotPassword("test@example.com");

        // Verify token exists
        java.util.List<PasswordResetToken> tokens = tokenRepository.findAll();
        assertFalse(tokens.isEmpty());
        assertEquals(user.getEmail(), tokens.get(0).getUser().getEmail());
    }
}
