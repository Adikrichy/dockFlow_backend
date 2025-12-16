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

        if(request.getCookies() != null) {
            for(Cookie cookie : request.getCookies()) {
                if(cookie.getName().equals("jwt") || cookie.getName().equals("JWT")){
                    cookie.setValue("null");
                    cookie.setPath("/");
                    cookie.setMaxAge(0);
                    servletResponse.addCookie(cookie);
                }
            }
        }

        Cookie jwtCookie = new Cookie("jwtWithCompany", response.getJwt());
        jwtCookie.setHttpOnly(true);
        jwtCookie.setSecure(true);
        jwtCookie.setPath("/");
        jwtCookie.setMaxAge(3600 * 24);
        servletResponse.addCookie(jwtCookie);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/update")
    public ResponseEntity<CompanyResponse> updateCompany(@RequestBody CompanyRequest companyRequest, HttpServletRequest request){

        Cookie[] cookies = request.getCookies();
        String token = null;

        if(cookies != null){
            token = Arrays.stream(cookies)
                    .filter(cookie -> "jwtWithCompany".equals(cookie.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);


        }

        CompanyResponse response = companyService.updateCompany(companyRequest,token);
        return ResponseEntity.ok(response);
    }
}
