package org.aldousdev.dockflowbackend.controllers;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.aldousdev.dockflowbackend.auth.dto.request.CompanyRequest;
import org.aldousdev.dockflowbackend.auth.dto.response.CompanyResponse;
import org.aldousdev.dockflowbackend.auth.dto.response.CreateCompanyResponse;
import org.aldousdev.dockflowbackend.service.impls.AuthServiceImpl;
import org.aldousdev.dockflowbackend.service.impls.CompanyServiceImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/company")
public class CompanyController {
    private final CompanyServiceImpl companyService;
    private final AuthServiceImpl authService;

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
