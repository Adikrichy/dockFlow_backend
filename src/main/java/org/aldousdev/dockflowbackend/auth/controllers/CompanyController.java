package org.aldousdev.dockflowbackend.auth.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.aldousdev.dockflowbackend.auth.dto.request.CompanyRequest;
import org.aldousdev.dockflowbackend.auth.dto.request.CreateRoleRequest;
import org.aldousdev.dockflowbackend.auth.dto.response.CompanyResponse;
import org.aldousdev.dockflowbackend.auth.dto.response.CreateCompanyResponse;
import org.aldousdev.dockflowbackend.auth.dto.response.CreateRoleResponse;
import org.aldousdev.dockflowbackend.auth.entity.Company;
import org.aldousdev.dockflowbackend.auth.entity.CompanyRoleEntity;
import org.aldousdev.dockflowbackend.auth.entity.Membership;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.auth.enums.CompanyRole;
import org.aldousdev.dockflowbackend.auth.repository.CompanyRoleEntityRepository;
import org.aldousdev.dockflowbackend.auth.service.impls.AuthServiceImpl;
import org.aldousdev.dockflowbackend.auth.service.impls.CompanyServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/company")
@Tag(name = "Company", description = "Управление компаниями и ролями в системе")
public class CompanyController {
    private final CompanyServiceImpl companyService;
    private final AuthServiceImpl authService;
    private final CompanyRoleEntityRepository companyRoleEntityRepository;

    @PostMapping("/create")
    @Operation(summary = "Создать новую компанию", 
            description = "Создает новую компанию и делает текущего пользователя CEO. " +
                    "Обновляет JWT токен с информацией о компании")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Компания успешно создана",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CreateCompanyResponse.class))),
            @ApiResponse(responseCode = "400", description = "Некорректные данные компании"),
            @ApiResponse(responseCode = "401", description = "Требуется аутентификация")
    })
    public ResponseEntity<CreateCompanyResponse> createCompany(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Данные компании")
            @RequestBody CompanyRequest companyRequest,
            HttpServletResponse servletResponse,
            HttpServletRequest request) {
        CreateCompanyResponse response = companyService.create(companyRequest);

        clearAuthCookies(request, servletResponse);

        Cookie jwtCookie = new Cookie("jwtWithCompany", response.getJwt());
        jwtCookie.setHttpOnly(true);
        jwtCookie.setSecure(true);
        jwtCookie.setPath("/");
        jwtCookie.setMaxAge(3600 * 24);
        servletResponse.addCookie(jwtCookie);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Обновить информацию компании", 
            description = "Обновляет данные компании (название, описание и т.д.). " +
                    "Доступно только для CEO компании")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Компания успешно обновлена",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CompanyResponse.class))),
            @ApiResponse(responseCode = "400", description = "Некорректные данные"),
            @ApiResponse(responseCode = "401", description = "Требуется аутентификация"),
            @ApiResponse(responseCode = "403", description = "Только CEO может обновлять компанию"),
            @ApiResponse(responseCode = "404", description = "Компания не найдена")
    })
    public ResponseEntity<CompanyResponse> updateCompany(
            @Parameter(description = "ID компании", required = true)
            @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Обновленные данные компании")
            @RequestBody CompanyRequest companyRequest, 
            HttpServletRequest request){

        Cookie[] cookies = request.getCookies();
        String token = null;

        if(cookies != null){
            token = Arrays.stream(cookies)
                    .filter(cookie -> "jwtWithCompany".equals(cookie.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);


        }

        CompanyResponse response = companyService.updateCompany(id,companyRequest,token);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{companyId}/enter")
    @Operation(summary = "Войти в компанию", 
            description = "Переключает контекст пользователя на другую компанию, обновляя JWT токен. " +
                    "Пользователь должен быть членом этой компании")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Успешно переключились на компанию"),
            @ApiResponse(responseCode = "401", description = "Требуется аутентификация"),
            @ApiResponse(responseCode = "403", description = "У вас нет доступа к этой компании"),
            @ApiResponse(responseCode = "404", description = "Компания не найдена")
    })
    public ResponseEntity<Void> enterCompany(
            @Parameter(description = "ID компании для входа", required = true)
            @PathVariable Long companyId, 
            HttpServletResponse response,
            HttpServletRequest request){
        String jwt = companyService.enterCompany(companyId);

        clearAuthCookies(request, response);

        Cookie cookie = new Cookie("jwtWithCompany", jwt);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(3600 * 24);
        response.addCookie(cookie);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/exit")
    @Operation(summary = "Выйти из компании", 
            description = "Переключает пользователя обратно на режим без компании. " +
                    "JWT токен обновляется без контекста компании")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Успешно вышли из компании"),
            @ApiResponse(responseCode = "401", description = "Требуется аутентификация")
    })
    public ResponseEntity<Void> exitCompany(HttpServletResponse response){
        String jwt = companyService.leaveCompany();

        Cookie cookie = new Cookie("jwtWithCompany", jwt);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/roles")
    @Operation(summary = "Создать новую роль в компании", 
            description = "Создает пользовательскую роль в компании с заданным уровнем прав. " +
                    "Доступно только для CEO компании")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Роль успешно создана",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CreateRoleResponse.class))),
            @ApiResponse(responseCode = "400", description = "Некорректные данные роли"),
            @ApiResponse(responseCode = "401", description = "Требуется аутентификация"),
            @ApiResponse(responseCode = "403", description = "Только CEO может создавать роли")
    })
    public ResponseEntity<CreateRoleResponse> createRole(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Данные для создания роли")
            @RequestBody @Valid CreateRoleRequest request){
        User currentUser = authService.getCurrentUser();
        Company company = currentUser.getMemberships().stream()
                .filter(m -> "CEO".equals(m.getRole().getName()))
                .findFirst()
                .map(Membership::getCompany)
                .orElseThrow(()-> new RuntimeException("Only Ceo can create role"));

        CompanyRoleEntity role = CompanyRoleEntity.builder()
                .name(request.getRoleName())
                .level(request.getLevel())
                .isSystem(false)
                .company(company)
                .build();
        companyRoleEntityRepository.save(role);

        CreateRoleResponse response = new CreateRoleResponse(
                role.getId(),
                role.getName(),
                role.getLevel(),
                role.getIsSystem()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/getAllRoles")
    @Operation(summary = "Получить все роли компании", 
            description = "Возвращает список всех ролей (системных и пользовательских) в компании текущего пользователя")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список ролей успешно получен",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CreateRoleResponse.class))),
            @ApiResponse(responseCode = "401", description = "Требуется аутентификация")
    })
    public ResponseEntity<List<CreateRoleResponse>> getAllRoles(){
        List<CreateRoleResponse> roles = companyService.getAllRoles();
        return ResponseEntity.status(HttpStatus.OK).body(roles);
    }

    private void clearAuthCookies(HttpServletRequest request, HttpServletResponse response) {
        if (request.getCookies() == null) return;

        for (Cookie cookie : request.getCookies()) {
            if (
                    "jwt".equals(cookie.getName()) ||
                            "JWT".equals(cookie.getName()) ||
                            "jwtWithCompany".equals(cookie.getName())
            ) {
                Cookie dead = new Cookie(cookie.getName(), "");
                dead.setPath("/");
                dead.setHttpOnly(true);
                dead.setSecure(true);
                dead.setMaxAge(0);
                response.addCookie(dead);
            }
        }
    }


}
