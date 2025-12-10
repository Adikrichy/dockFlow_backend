package org.aldousdev.dockflowbackend.service;

import org.aldousdev.dockflowbackend.auth.dto.request.CompanyRequest;
import org.aldousdev.dockflowbackend.auth.dto.response.CompanyResponse;
import org.aldousdev.dockflowbackend.auth.entity.User;

import java.util.List;

public interface CompanyService {
    CompanyResponse create(CompanyRequest companyRequest);
    List<CompanyResponse> getUserCompanies();
    CompanyResponse updateCompany(Long id,CompanyRequest companyRequest);
    void deleteCompany(Long id);
}
