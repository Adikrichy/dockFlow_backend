package org.aldousdev.dockflowbackend.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.aldousdev.dockflowbackend.auth.dto.request.RegisterRequest;
import org.aldousdev.dockflowbackend.auth.dto.response.RegisterResponse;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.auth.enums.Status;
import org.aldousdev.dockflowbackend.auth.repository.UserRepository;
import org.aldousdev.dockflowbackend.service.impls.UserServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor

public class RegisterController {

    private final UserServiceImpl userService;
    private final UserRepository userRepository;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@RequestBody @Valid RegisterRequest registerRequest){
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(registerRequest));
    }

    @PostMapping("/auth/verify-email")
    public ResponseEntity<String> verifyEmail(@RequestParam String email, @RequestParam String verificationCode){
        User user = userRepository.findByEmail(email)
                .orElseThrow(()-> new RuntimeException("User with email "+ email + " not found"));
        if(user.getEmailVerificationCode() != null && user.getEmailVerificationCode().equals(verificationCode)){
            user.setEmailVerified(true);
            user.setStatus(Status.ACTIVE);
            user.setEmailVerificationCode(null);
            userRepository.save(user);
            return ResponseEntity.status(HttpStatus.OK).body("Email verified");
        }
        else{
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid verification code");
        }
    }
}
