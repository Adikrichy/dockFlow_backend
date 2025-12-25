package org.aldousdev.dockflowbackend.auth.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.aldousdev.dockflowbackend.auth.dto.request.LoginRequest;
import org.aldousdev.dockflowbackend.auth.dto.response.LoginResponse;
import org.aldousdev.dockflowbackend.auth.service.impls.AuthServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Управление аутентификацией и авторизацией")
public class AuthController {
    private final AuthServiceImpl authService;

    @PostMapping("/login")
    @Operation(summary = "Вход в систему", 
            description = "Аутентифицирует пользователя по email и пароля. " +
                    "Возвращает JWT токен в HttpOnly cookie для безопасности. " +
                    "Токен действителен 24 часа")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Вход успешен",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "400", description = "Некорректный email или пароль"),
            @ApiResponse(responseCode = "401", description = "Пользователь не активирован")
    })
    public ResponseEntity<LoginResponse> login(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Email и пароль пользователя")
            @RequestBody LoginRequest loginRequest,
            HttpServletResponse response) {
        LoginResponse loginResponse = authService.login(loginRequest, response);
        return ResponseEntity.status(HttpStatus.OK).body(loginResponse);
    }

    @PostMapping("/logout")
    @Operation(summary = "Выход из системы", 
            description = "Аннулирует JWT токен пользователя, удаляя его из cookies. " +
                    "После этого пользователь должен заново войти в систему")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Выход успешен"),
            @ApiResponse(responseCode = "401", description = "Требуется аутентификация")
    })
    public ResponseEntity<LoginResponse> logout(HttpServletResponse response) {
        authService.logout(response);
        return ResponseEntity.status(HttpStatus.OK).build();
    }
}
