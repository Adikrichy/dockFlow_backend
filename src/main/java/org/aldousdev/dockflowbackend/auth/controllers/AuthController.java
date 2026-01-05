package org.aldousdev.dockflowbackend.auth.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.aldousdev.dockflowbackend.auth.dto.request.LoginRequest;
import org.aldousdev.dockflowbackend.auth.dto.response.CompanyMembershipResponse;
import org.aldousdev.dockflowbackend.auth.dto.response.LoginResponse;
import org.aldousdev.dockflowbackend.auth.dto.response.UserContextResponse;
import org.aldousdev.dockflowbackend.auth.dto.response.UserResponse;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.auth.security.JWTService;
import org.aldousdev.dockflowbackend.auth.security.JwtAuthenticationToken;
import org.aldousdev.dockflowbackend.auth.service.impls.AuthServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Управление аутентификацией и авторизацией")
public class AuthController {
    private final AuthServiceImpl authService;
    private final JWTService jwtService;

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

    @PostMapping("/refresh")
    @Operation(summary = "Обновление токена доступа",
            description = "Обновляет access token используя refresh token. " +
                    "Требует валидный refresh token в Authorization header. " +
                    "Возвращает новый access token в HttpOnly cookie")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Токен успешно обновлен",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "400", description = "Некорректный refresh token"),
            @ApiResponse(responseCode = "401", description = "Refresh token истек или недействителен")
    })
    public ResponseEntity<LoginResponse> refreshToken(
            HttpServletRequest request,
            HttpServletResponse response) {

        LoginResponse loginResponse = authService.refreshTokenFromCookie(request, response);
        return ResponseEntity.ok(loginResponse);
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Запрос сброса пароля",
            description = "Отправляет на email пользователя ссылку для сброса пароля")
    public ResponseEntity<Void> forgotPassword(@RequestBody org.aldousdev.dockflowbackend.auth.dto.request.ForgotPasswordRequest request) {
        authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Сброс пароля",
            description = "Устанавливает новый пароль, используя токен из письма")
    public ResponseEntity<Void> resetPassword(@RequestBody org.aldousdev.dockflowbackend.auth.dto.request.ResetPasswordRequest request) {
        authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserContextResponse> getMyContext(HttpServletRequest request){
        User user = authService.getCurrentUser();

        // Reload user with memberships to ensure they are loaded
        User userWithMemberships = authService.getUserWithMemberships(user.getEmail());

        // Debug: Log memberships count
        System.out.println("User memberships count: " + (userWithMemberships.getMemberships() != null ? userWithMemberships.getMemberships().size() : 0));

        List<CompanyMembershipResponse> companies = userWithMemberships.getMemberships() != null
                ? userWithMemberships.getMemberships().stream()
                .map(m->CompanyMembershipResponse.builder()
                        .companyId(m.getCompany().getId())
                        .companyName(m.getCompany().getName())
                        .description(m.getCompany().getDescription())
                        .roleName(m.getRole().getName())
                        .roleLevel(m.getRole().getLevel())
                        .build())
                .toList()
                : java.util.Collections.emptyList();

        // Debug: Log companies count
        System.out.println("Companies response count: " + companies.size());

        // ============ ИСПРАВЛЕНИЕ: проверяем cookie jwtWithCompany ============
        CompanyMembershipResponse currentCompany = null;

        // Получаем cookie jwtWithCompany
        String jwtWithCompany = null;
        if (request.getCookies() != null) {
            jwtWithCompany = java.util.Arrays.stream(request.getCookies())
                    .filter(cookie -> "jwtWithCompany".equals(cookie.getName()))
                    .map(jakarta.servlet.http.Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }

        // Если есть cookie jwtWithCompany - извлекаем companyId из НЕГО
        if (jwtWithCompany != null && jwtService.isTokenValid(jwtWithCompany)) {
            Long companyIdFromToken = jwtService.extractCompanyId(jwtWithCompany);

            if (companyIdFromToken != null) {
                currentCompany = companies.stream()
                        .filter(c -> c.getCompanyId().equals(companyIdFromToken))
                        .findFirst()
                        .orElse(null);
            }
        }
        // ============ КОНЕЦ ИСПРАВЛЕНИЯ ============

        UserResponse userResponse = UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .userType(user.getUserType().name())
                .companyRole(currentCompany != null ? currentCompany.getRoleName() : null)
                .build();

        UserContextResponse response = UserContextResponse.builder()
                .user(userResponse)
                .companies(companies)
                .currentCompany(currentCompany)
                .build();

        return ResponseEntity.ok(response);
    }


}
