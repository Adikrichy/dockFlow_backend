package org.aldousdev.dockflowbackend.auth.service;

import org.aldousdev.dockflowbackend.auth.dto.request.CompanyRequest;
import org.aldousdev.dockflowbackend.auth.dto.request.UpdateRoleRequest;
import org.aldousdev.dockflowbackend.auth.dto.response.*;
import org.aldousdev.dockflowbackend.auth.entity.Company;
import org.aldousdev.dockflowbackend.auth.entity.CompanyRoleEntity;
import org.aldousdev.dockflowbackend.auth.entity.User;

import java.util.List;

public interface CompanyService {
    CreateCompanyResponse create(CompanyRequest companyRequest);
    List<CompanyResponse> getUserCompanies();
    CompanyResponse updateCompany(Long id,CompanyRequest companyRequest, String token);
    String enterCompany(Long id, byte[] keyFileBytes);
    String leaveCompany();
    CompanyRoleEntity initDefaultRoles(Company company, User currentUser);
    List<CreateRoleResponse> getAllRoles(Long companyId);
    void deleteCompany(Long id);
    CompanyResponse getCompanyById(Long id);
    List<UserResponse> getCompanyMembers(Long companyId);
    byte[] joinCompany(Long companyId);
    UpdateRoleResponse updateRole(Long companyId, UpdateRoleRequest request);
    void deleteRole(Long companyId);
    List<CompanyResponse> listAll();
    List<CompanyResponse> searchByName(String name);
    void updateMemberRole(Long userId, Long roleId);
}
