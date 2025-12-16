package org.aldousdev.dockflowbackend.service;

import org.aldousdev.dockflowbackend.auth.dto.request.CompanyRequest;
import org.aldousdev.dockflowbackend.auth.dto.response.CompanyResponse;
import org.aldousdev.dockflowbackend.auth.dto.response.CreateCompanyResponse;

import java.util.List;

public interface CompanyService {
    CreateCompanyResponse create(CompanyRequest companyRequest);
    List<CompanyResponse> getUserCompanies();
    CompanyResponse updateCompany(CompanyRequest companyRequest, String token);
    void deleteCompany(Long id);
}
