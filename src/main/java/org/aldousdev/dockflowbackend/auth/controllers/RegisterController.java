package org.aldousdev.dockflowbackend.auth.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.aldousdev.dockflowbackend.auth.dto.request.RegisterRequest;
import org.aldousdev.dockflowbackend.auth.dto.response.RegisterResponse;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.auth.enums.Status;
import org.aldousdev.dockflowbackend.auth.repository.UserRepository;
import org.aldousdev.dockflowbackend.auth.service.impls.UserServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Registration", description = "Регистрация и верификация пользователей")
public class RegisterController {

    private final UserServiceImpl userService;
    private final UserRepository userRepository;

    @PostMapping("/register")
    @Operation(summary = "Регистрация нового пользователя", 
            description = "Создает новый аккаунт пользователя и отправляет email для верификации. " +
                    "Пользователь не сможет войти до подтверждения email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Регистрация успешна",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = RegisterResponse.class))),
            @ApiResponse(responseCode = "400", description = "Email уже зарегистрирован или данные некорректны")
    })
    public ResponseEntity<RegisterResponse> register(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Данные для регистрации")
            @RequestBody @Valid RegisterRequest registerRequest){
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(registerRequest));
    }

    @PostMapping("/auth/verify-email")
    @Operation(summary = "Верифицировать email", 
            description = "Активирует аккаунт пользователя путем подтверждения кода верификации, " +
                    "отправленного на email. После этого пользователь сможет войти в систему")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Email успешно верифицирован"),
            @ApiResponse(responseCode = "400", description = "Некорректный код верификации"),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден")
    })
    public ResponseEntity<String> verifyEmail(
            @Parameter(description = "Email пользователя", required = true)
            @RequestParam String email, 
            @Parameter(description = "Код верификации из письма", required = true)
            @RequestParam String verificationCode){
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
