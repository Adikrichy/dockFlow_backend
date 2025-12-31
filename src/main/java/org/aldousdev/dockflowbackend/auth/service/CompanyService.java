package org.aldousdev.dockflowbackend.auth.service;

import org.aldousdev.dockflowbackend.auth.dto.request.CompanyRequest;
import org.aldousdev.dockflowbackend.auth.dto.response.CompanyResponse;
import org.aldousdev.dockflowbackend.auth.dto.response.CreateCompanyResponse;
import org.aldousdev.dockflowbackend.auth.dto.response.CreateRoleResponse;
import org.aldousdev.dockflowbackend.auth.dto.response.UserResponse;
import org.aldousdev.dockflowbackend.auth.entity.Company;
import org.aldousdev.dockflowbackend.auth.entity.CompanyRoleEntity;
import org.aldousdev.dockflowbackend.auth.entity.User;

import java.util.List;

public interface CompanyService {
    CreateCompanyResponse create(CompanyRequest companyRequest);
    List<CompanyResponse> getUserCompanies();
    CompanyResponse updateCompany(Long id,CompanyRequest companyRequest, String token);
    String enterCompany(Long id);
    String leaveCompany();
    CompanyRoleEntity initDefaultRoles(Company company, User currentUser);
    List<CreateRoleResponse> getAllRoles(Long companyId);
    void deleteCompany(Long id);
    CompanyResponse getCompanyById(Long id);
    List<UserResponse> getCompanyMembers(Long companyId);
    void joinCompany(Long companyId);
}
