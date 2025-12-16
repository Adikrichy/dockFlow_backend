package org.aldousdev.dockflowbackend.service;

import jakarta.servlet.http.HttpServletRequest;
import org.aldousdev.dockflowbackend.auth.dto.request.CompanyRequest;
import org.aldousdev.dockflowbackend.auth.dto.response.CompanyResponse;
import org.aldousdev.dockflowbackend.auth.dto.response.CreateCompanyResponse;

import java.util.List;

public interface CompanyService {
    CreateCompanyResponse create(CompanyRequest companyRequest);
    List<CompanyResponse> getUserCompanies();
    CompanyResponse updateCompany(Long id,CompanyRequest companyRequest, String token);
    String enterCompany(Long id);
    String leaveCompany();
    void deleteCompany(Long id);
}
