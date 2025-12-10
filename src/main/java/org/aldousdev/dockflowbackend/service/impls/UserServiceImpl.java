package org.aldousdev.dockflowbackend.service.impls;

import lombok.RequiredArgsConstructor;
import org.aldousdev.dockflowbackend.auth.dto.request.RegisterRequest;
import org.aldousdev.dockflowbackend.auth.dto.response.RegisterResponse;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.auth.enums.Status;
import org.aldousdev.dockflowbackend.auth.enums.UserType;
import org.aldousdev.dockflowbackend.auth.exceptions.EmailAlreadyExistsException;
import org.aldousdev.dockflowbackend.auth.repository.UserRepository;
import org.aldousdev.dockflowbackend.mapper.UserMapper;
import org.aldousdev.dockflowbackend.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final EmailServiceImpl emailService;

    @Transactional
    @Override
    public RegisterResponse register(RegisterRequest registerRequest) {
        if(userRepository.existsByEmail(registerRequest.getEmail())){
            throw new EmailAlreadyExistsException("Email already in use");
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

        emailService.sendVerificationEmail(savedUser.getEmail(), verificationCode);

        return userMapper.toRegisterResponse(savedUser);
    }


}
