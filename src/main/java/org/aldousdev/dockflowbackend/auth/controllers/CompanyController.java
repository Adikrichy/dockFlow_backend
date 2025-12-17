package org.aldousdev.dockflowbackend.auth.controllers;

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
public class CompanyController {
    private final CompanyServiceImpl companyService;
    private final AuthServiceImpl authService;
    private final CompanyRoleEntityRepository companyRoleEntityRepository;

    @PostMapping("/create")
    public ResponseEntity<CreateCompanyResponse> createCompany(@RequestBody CompanyRequest companyRequest,
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
    public ResponseEntity<CompanyResponse> updateCompany(@PathVariable Long id,@RequestBody CompanyRequest companyRequest, HttpServletRequest request){

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
    public ResponseEntity<Void> enterCompany(@PathVariable Long companyId, HttpServletResponse response,HttpServletRequest request){
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
    public ResponseEntity<CreateRoleResponse> createRole(@RequestBody @Valid CreateRoleRequest request){
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
