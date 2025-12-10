package org.aldousdev.dockflowbackend.controllers;

import lombok.RequiredArgsConstructor;
import org.aldousdev.dockflowbackend.auth.dto.request.CompanyRequest;
import org.aldousdev.dockflowbackend.auth.dto.response.CompanyResponse;
import org.aldousdev.dockflowbackend.service.impls.AuthServiceImpl;
import org.aldousdev.dockflowbackend.service.impls.CompanyServiceImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.aldousdev.dockflowbackend.auth.entity.User;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/company")
public class CompanyController {
    private final CompanyServiceImpl companyService;
    private final AuthServiceImpl authService;

    @PostMapping("/create")
    public ResponseEntity<CompanyResponse> createCompany(@RequestBody CompanyRequest companyRequest){
        CompanyResponse response = companyService.create(companyRequest);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CompanyResponse> updateCompany(@PathVariable Long id,@RequestBody CompanyRequest companyRequest){
        CompanyResponse response = companyService.updateCompany(id, companyRequest);
        return ResponseEntity.ok(response);
    }
}
