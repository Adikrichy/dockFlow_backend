package org.aldousdev.dockflowbackend.ai.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.auth.enums.Status;
import org.aldousdev.dockflowbackend.auth.enums.UserType;
import org.aldousdev.dockflowbackend.auth.repository.UserRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiUserInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String AI_EMAIL = "ai@dockflow.com";

    @EventListener(ApplicationReadyEvent.class)
    public void initAiUser() {
        if (userRepository.findByEmail(AI_EMAIL).isPresent()) {
            log.info("AI User ({}) already exists.", AI_EMAIL);
            return;
        }

        log.info("Creating AI User ({})", AI_EMAIL);
        
        User aiUser = User.builder()
                .email(AI_EMAIL)
                .firstName("AI")
                .lastName("Assistant")
                .password(passwordEncoder.encode("ai-ignored-password-" + java.util.UUID.randomUUID()))
                .status(Status.ACTIVE)
                .userType(UserType.PLATFORM_USER)
                .emailVerified(true)
                .build();

        userRepository.save(aiUser);
        log.info("AI User created successfully with ID: {}", aiUser.getId());
    }
}
