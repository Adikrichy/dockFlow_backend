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
import org.aldousdev.dockflowbackend.auth.components.RequiresRoleLevel;
import org.aldousdev.dockflowbackend.auth.dto.request.CompanyRequest;
import org.aldousdev.dockflowbackend.auth.dto.request.CreateRoleRequest;
import org.aldousdev.dockflowbackend.auth.dto.request.UpdateMemberRoleRequest;
import org.aldousdev.dockflowbackend.auth.dto.request.UpdateRoleRequest;
import org.aldousdev.dockflowbackend.auth.dto.response.*;
import org.aldousdev.dockflowbackend.auth.entity.Company;
import org.aldousdev.dockflowbackend.auth.entity.CompanyRoleEntity;
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
    private final org.aldousdev.dockflowbackend.auth.security.JWTService jwtService;

    @PostMapping("/create")
    @Operation(summary = "Создать новую компанию", 
            description = "Создает новую компанию и делает текущего пользователя CEO. " +
                    "Обновляет JWT токен с информацией о компании. " +
                    "Возвращает PKCS#12 ключ для доступа к компании")
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

        // Return JSON response with company data and key file as base64
        // Frontend will decode and download the file
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

    @PostMapping("/join/{companyId}")
    @Operation(summary = "Присоединиться к компании",
            description = "Добавляет текущего пользователя в список участников компании с ролью по умолчанию (Worker). " +
                    "Возвращает PKCS#12 ключ для последующего входа")
    public ResponseEntity<byte[]> joinCompany(
            @PathVariable Long companyId) {
        // Always use default password for key encryption
        byte[] keyFileBytes = companyService.joinCompany(companyId);
        
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=company_" + companyId + "_key.p12")
                .header("Content-Type", "application/x-pkcs12")
                .body(keyFileBytes);
    }

    @PostMapping("/enter/{companyId}")
    @Operation(summary = "Войти в компанию", 
            description = "Переключает контекст пользователя на другую компанию, обновляя JWT токен. " +
                    "Требует загрузку PKCS#12 ключа для проверки доступа")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Успешно переключились на компанию"),
            @ApiResponse(responseCode = "401", description = "Требуется аутентификация"),
            @ApiResponse(responseCode = "403", description = "Неверный ключ или пароль"),
            @ApiResponse(responseCode = "404", description = "Компания не найдена")
    })
    public ResponseEntity<Void> enterCompany(
            @Parameter(description = "ID компании для входа", required = true)
            @PathVariable Long companyId,
            @RequestParam("keyFile") org.springframework.web.multipart.MultipartFile keyFile,
            HttpServletResponse response,
            HttpServletRequest request) throws java.io.IOException {
        
        byte[] keyFileBytes = keyFile.getBytes();
        String jwt = companyService.enterCompany(companyId, keyFileBytes);

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
                    "Доступно только для пользователей с уровнем доступа 100 (например, CEO)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Роль успешно создана",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CreateRoleResponse.class))),
            @ApiResponse(responseCode = "400", description = "Некорректные данные роли"),
            @ApiResponse(responseCode = "401", description = "Требуется аутентификация"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав для создания роли")
    })
    public ResponseEntity<CreateRoleResponse> createRole(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Данные для создания роли")
            @RequestBody @Valid CreateRoleRequest request,
            HttpServletRequest servletRequest){
        
        String token = getTokenFromRequest(servletRequest);
        if (token == null || !jwtService.isTokenValid(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Long companyId = jwtService.extractCompanyId(token);
        Integer roleLevel = jwtService.extractCompanyRoleLevel(token);

        if (roleLevel == null || roleLevel < 100) {
            throw new RuntimeException("Only users with level 100 (CEO) can create roles");
        }

        Company company = Company.builder().id(companyId).build();

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
            description = "Возвращает список всех ролей в компании, в которую выполнен вход")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список ролей успешно получен",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CreateRoleResponse.class))),
            @ApiResponse(responseCode = "401", description = "Требуется аутентификация")
    })
    public ResponseEntity<List<CreateRoleResponse>> getAllRoles(HttpServletRequest request){
        String token = getTokenFromRequest(request);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Long companyId = jwtService.extractCompanyId(token);
        List<CreateRoleResponse> roles = companyService.getAllRoles(companyId);
        return ResponseEntity.status(HttpStatus.OK).body(roles);
    }

    @GetMapping("/current")
    @Operation(summary = "Получить текущую компанию",
            description = "Возвращает информацию о компании, в которую выполнен вход (через токен jwtWithCompany)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Информация о компании",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CompanyResponse.class))),
            @ApiResponse(responseCode = "404", description = "Компания не найдена или вход не выполнен")
    })
    public ResponseEntity<CompanyResponse> getCurrentCompany(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        String token = null;

        if (cookies != null) {
            token = Arrays.stream(cookies)
                    .filter(cookie -> "jwtWithCompany".equals(cookie.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }

        if (token == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            if (!jwtService.isTokenValid(token)) {
                return ResponseEntity.status(401).build();
            }
            Long companyId = jwtService.extractCompanyId(token);
            CompanyResponse companyResponse = companyService.getCompanyById(companyId);
            return ResponseEntity.ok(companyResponse);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/list")
    @Operation(summary = "Получить список всех компаний",
            description = "Возвращает список всех компаний в системе")
    public ResponseEntity<List<CompanyResponse>> listAllCompanies() {
        return ResponseEntity.ok(companyService.listAll());
    }

    @GetMapping("/search")
    @Operation(summary = "Поиск компаний по названию",
            description = "Возвращает список компаний, название которых содержит указанную строку")
    public ResponseEntity<List<CompanyResponse>> searchCompanies(@RequestParam String name) {
        return ResponseEntity.ok(companyService.searchByName(name));
    }

    @GetMapping("/members")
    @Operation(summary = "Получить участников компании",
            description = "Возвращает список всех участников в компании, в которую выполнен вход")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список участников успешно получен"),
            @ApiResponse(responseCode = "401", description = "Требуемая аутентификация")
    })
    public ResponseEntity<List<UserResponse>> getCompanyMembers(HttpServletRequest request) {
        String token = getTokenFromRequest(request);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Long companyId = jwtService.extractCompanyId(token);
        List<UserResponse> members = companyService.getCompanyMembers(companyId);
        return ResponseEntity.ok(members);
    }

    @PutMapping("/roles/{roleId}")
    @RequiresRoleLevel(100)
    public ResponseEntity<UpdateRoleResponse> updateRole(
            @PathVariable Long roleId,
            @Valid @RequestBody UpdateRoleRequest request) {

        UpdateRoleResponse response = companyService.updateRole(roleId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/roles/{roleId}")
    @RequiresRoleLevel(100)
    public ResponseEntity<Void> deleteRole(@PathVariable Long roleId) {
        companyService.deleteRole(roleId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/members/{userId}/role")
    @Operation(summary = "Обновить роль участника", 
            description = "Позволяет CEO изменить роль сотрудника внутри компании.")
    @RequiresRoleLevel(100)
    public ResponseEntity<Void> updateMemberRole(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateMemberRoleRequest request) {
        
        companyService.updateMemberRole(userId, request.getRoleId());
        return ResponseEntity.ok().build();
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            return Arrays.stream(cookies)
                    .filter(cookie -> "jwtWithCompany".equals(cookie.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }
        return null;
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
