package org.aldousdev.dockflowbackend.auth.service.impls;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.auth.dto.request.RegisterRequest;
import org.aldousdev.dockflowbackend.auth.dto.response.RegisterResponse;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.auth.enums.Status;
import org.aldousdev.dockflowbackend.auth.enums.UserType;
import org.aldousdev.dockflowbackend.auth.exceptions.EmailAlreadyExistsException;
import org.aldousdev.dockflowbackend.auth.repository.UserRepository;
import org.aldousdev.dockflowbackend.auth.mapper.UserMapper;
import org.aldousdev.dockflowbackend.auth.service.UserService;
import org.aldousdev.dockflowbackend.auth.validators.PasswordValidator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final EmailServiceImpl emailService;

    @Transactional
    @Override
    public RegisterResponse register(RegisterRequest registerRequest) {
        log.info("Registration attempt for email: {}", registerRequest.getEmail());
        
        if(userRepository.existsByEmail(registerRequest.getEmail())){
            log.warn("Email already exists: {}", registerRequest.getEmail());
            throw new EmailAlreadyExistsException("Email already in use");
        }

        if(!PasswordValidator.isValid(registerRequest.getPassword())) {
            log.warn("Password validation failed for email: {}", registerRequest.getEmail());
            throw new IllegalArgumentException(PasswordValidator.getRequirements());
        }

        User user = User.builder()
                .email(registerRequest.getEmail())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .firstName(registerRequest.getFirstName())
                .lastName(registerRequest.getLastName())
                .status(Status.PENDING)
                .userType(UserType.PLATFORM_USER)
                .emailVerified(false)
                .build();
        User savedUser = userRepository.save(user);

        String verificationCode = UUID.randomUUID().toString();
        savedUser.setEmailVerificationCode(verificationCode);
        userRepository.save(savedUser);

        try {
            emailService.sendVerificationEmail(savedUser.getEmail(), verificationCode);
            log.info("Verification email sent to: {}", savedUser.getEmail());
        } catch (Exception e) {
            log.error("Failed to send verification email to: {}", savedUser.getEmail(), e);
            throw new RuntimeException("Failed to send verification email: " + e.getMessage());
        }

        return userMapper.toRegisterResponse(savedUser);
    }

    @Override
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));
    }
}

