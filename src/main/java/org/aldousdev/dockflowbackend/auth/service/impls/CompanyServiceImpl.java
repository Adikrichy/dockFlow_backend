package org.aldousdev.dockflowbackend.auth.service.impls;

import lombok.RequiredArgsConstructor;
import org.aldousdev.dockflowbackend.auth.components.RequiresRoleLevel;
import org.aldousdev.dockflowbackend.auth.dto.request.CompanyRequest;
import org.aldousdev.dockflowbackend.auth.dto.request.UpdateRoleRequest;
import org.aldousdev.dockflowbackend.auth.dto.response.*;
import org.aldousdev.dockflowbackend.auth.entity.Company;
import org.aldousdev.dockflowbackend.auth.entity.CompanyRoleEntity;
import org.aldousdev.dockflowbackend.auth.entity.Membership;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.auth.enums.UserType;
import org.aldousdev.dockflowbackend.auth.exceptions.ForbiddenException;
import org.aldousdev.dockflowbackend.auth.exceptions.BadRequestException;
import org.aldousdev.dockflowbackend.auth.exceptions.ResourceNotFoundException;
import org.aldousdev.dockflowbackend.auth.repository.CompanyRepository;
import org.aldousdev.dockflowbackend.auth.repository.CompanyRoleEntityRepository;
import org.aldousdev.dockflowbackend.auth.repository.MembershipRepository;
import org.aldousdev.dockflowbackend.auth.repository.UserRepository;
import org.aldousdev.dockflowbackend.auth.security.JWTService;
import org.aldousdev.dockflowbackend.auth.mapper.CompanyMapper;
import org.aldousdev.dockflowbackend.auth.service.CompanyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CompanyServiceImpl implements CompanyService {
    private final CompanyRepository companyRepository;
    private final CompanyMapper companyMapper;
    private final AuthServiceImpl authService;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final JWTService jwtService;
    private final CompanyRoleEntityRepository companyRoleEntityRepository;
    private final org.aldousdev.dockflowbackend.auth.service.DigitalSignatureService digitalSignatureService;

    @Override
    public CreateCompanyResponse create(CompanyRequest request){
        User currentUser = authService.getCurrentUser();

        Company company = companyMapper.toEntity(request);
        company.setCreatedAt(LocalDateTime.now());
        company = companyRepository.save(company);

        CompanyRoleEntity ceoRole = null;

        if(request.isUseDefaultRoles() ){
            ceoRole = initDefaultRoles(company, currentUser);
        }  else{
             ceoRole = CompanyRoleEntity.builder()
                     .name("CEO")
                     .level(100)
                     .isSystem(true)
                     .company(company)
                     .build();
             companyRoleEntityRepository.save(ceoRole);

             Membership membership = Membership.builder()
                     .company(company)
                     .user(currentUser)
                     .role(ceoRole)
                     .build();
             membershipRepository.save(membership);
        }

        currentUser.setUserType(UserType.COMPANY_OWNER);
        userRepository.save(currentUser);

        // Always use default password for key encryption
        // This ensures that key verification works without requiring password input
        String keyPassword = "defaultPassword123";
        
        // Generate access key for the user (always with default password)
        org.aldousdev.dockflowbackend.auth.entity.CompanyAccessKey accessKey = 
            digitalSignatureService.generateAccessKey(currentUser, company, keyPassword);
        
        // Create key file bytes for download
        byte[] keyFileBytes = digitalSignatureService.createKeyFile(accessKey, keyPassword);

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", currentUser.getEmail());
        claims.put("userId", currentUser.getId());
        claims.put("userType", currentUser.getUserType().name());
        claims.put("companyRole", ceoRole.getName());
        claims.put("companyId", company.getId());
        claims.put("companyRoleLevel", ceoRole.getLevel());

        String jwt = jwtService.generateCompanyToken(currentUser,claims);

        CompanyResponse companyResponse = companyMapper.toDto(company);
        CreateCompanyResponse response = new CreateCompanyResponse();
        response.setCompany(companyResponse);
        response.setJwt(jwt);
        // Encode key file bytes as Base64 for JSON response
        response.setKeyFileBase64(java.util.Base64.getEncoder().encodeToString(keyFileBytes));

        return response;

    }

    @Override
    public List<CompanyResponse> getUserCompanies(){
        User currentUser = authService.getCurrentUser();
        return membershipRepository.findByUser(currentUser).stream()
                .map(Membership::getCompany)
                .map(companyMapper::toDto)
                .collect(Collectors.toList());

    }

    @Override
    @Transactional
    @RequiresRoleLevel(value = 80, message = "Only 80 and higher can update")
    public CompanyResponse updateCompany(Long id,CompanyRequest request, String token){
//        if(token == null || !jwtService.isTokenValid(token)){
//            throw new RuntimeException("Invalid token");
//        }
//
//        String role = jwtService.extractCompanyRole(token);
//        if(!"CEO".equals(role) && !"DIRECTOR".equals(role)){
//            throw new RuntimeException("Access denied: only CEO or Director can update company");
//        }


        Company company = companyRepository.findById(id)
                .orElseThrow(()-> new RuntimeException("Company not found"));

        User currentUser = authService.getCurrentUser();

//        Membership membership = membershipRepository.findByCompanyIdAndUserId(id, currentUser.getId())
//                .orElseThrow(() -> new RuntimeException("No access to this company"));
        membershipRepository.findByCompanyIdAndUserId(id, currentUser.getId())
                .orElseThrow(()-> new RuntimeException("No access to this company"));

//        String roleName = membership.getRole().getName();
//        if(!roleName.equals("CEO") && !roleName.equals("DIRECTOR")){
//            throw new RuntimeException("Access denied: Only CEO or Director can update company");
//        }

//        if(membership.getCompanyRole() != CompanyRole.CEO && membership.getCompanyRole() != CompanyRole.DIRECTOR){
//            throw new RuntimeException("Only Ceo and Director can update this company");
//        }


        companyMapper.updateCompany(request, company);

        return companyMapper.toDto(company);
    }

    @Override
    public void deleteCompany(Long companyId){
        User currentUser = authService.getCurrentUser();

        Membership membership = membershipRepository.findByCompanyIdAndUserId(companyId,currentUser.getId())
                .orElseThrow(() -> new RuntimeException("No access to this company"));

        String roleName = membership.getRole().getName();
        if(!roleName.equals("CEO") && !roleName.equals("DIRECTOR")){
            throw new RuntimeException("Access denied: Only CEO or Director can update company");
        }

//        if(membership.getCompanyRole() != CompanyRole.CEO){
//            throw new RuntimeException("Only Ceo can delete this company");
//        }

        companyRepository.deleteById(companyId);
    }

    @Override
    public String enterCompany(Long id, byte[] keyFileBytes){
        User user = authService.getCurrentUser();

        Membership membership = membershipRepository.findByCompanyIdAndUserId(
                id,user.getId()).orElseThrow(() -> new RuntimeException("No access to this company"));

        // Verify the key file before granting access (password not needed - using default)
        boolean isKeyValid = digitalSignatureService.verifyKeyFile(keyFileBytes, user.getId(), id);
        
        if (!isKeyValid) {
            throw new RuntimeException("Invalid key file");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", user.getEmail());
        claims.put("userId", user.getId());
        claims.put("userType", user.getUserType().name());
        claims.put("companyRole", membership.getRole().getName());
        claims.put("companyId", membership.getCompany().getId());
        claims.put("companyRoleLevel", membership.getRole().getLevel());



        return jwtService.generateCompanyToken(
                 user, claims);
    }

    @Override
    public String leaveCompany(){
        User user = authService.getCurrentUser();
        return jwtService.generateCompanyToken(user, null);
    }

    @Override
    public CompanyRoleEntity initDefaultRoles(Company company, User currentUser){
        CompanyRoleEntity ceo = CompanyRoleEntity.builder()
                .name("CEO")
                .level(100)
                .isSystem(true)
                .company(company)
                .build();

        CompanyRoleEntity director = CompanyRoleEntity.builder()
                .name("Director")
                .level(80)
                .isSystem(true)
                .company(company)
                .build();

        CompanyRoleEntity manager = CompanyRoleEntity.builder()
                .name("Manager")
                .level(60)
                .isSystem(true)
                .company(company)
                .build();

        CompanyRoleEntity worker = CompanyRoleEntity.builder()
                .name("Worker")
                .level(10)
                .isSystem(true)
                .company(company)
                .build();

        companyRoleEntityRepository.saveAll(List.of(ceo, director, manager, worker));

        Membership ceoMembership = Membership.builder()
                .company(company)
                .user(currentUser)
                .role(ceo)
                .build();
        membershipRepository.save(ceoMembership);

        return ceo;
    }

    @Override
    public List<CreateRoleResponse> getAllRoles(Long companyId){
        User currentUser = authService.getCurrentUser();
        
        // Verify membership
        membershipRepository.findByCompanyIdAndUserId(companyId, currentUser.getId())
                .orElseThrow(() -> new RuntimeException("No access to this company"));

        return companyRoleEntityRepository.findByCompanyId(companyId).stream()
                .map(role -> new CreateRoleResponse(
                        role.getId(),
                        role.getName(),
                        role.getLevel(),
                        role.getIsSystem()
                ))
                .collect(Collectors.toList());
    }

    @Override
    public List<UserResponse> getCompanyMembers(Long companyId) {
        User currentUser = authService.getCurrentUser();
        
        // Verify membership
        membershipRepository.findByCompanyIdAndUserId(companyId, currentUser.getId())
                .orElseThrow(() -> new RuntimeException("No access to this company"));

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        return membershipRepository.findByCompany(company).stream()
                .map(m -> {
                    User u = m.getUser();
                    return UserResponse.builder()
                            .id(u.getId())
                            .email(u.getEmail())
                            .firstName(u.getFirstName())
                            .lastName(u.getLastName())
                            .companyRole(m.getRole().getName())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    public CompanyResponse getCompanyById(Long id) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Company not found"));
        return companyMapper.toDto(company);
    }

    @Override
    @Transactional
    public byte[] joinCompany(Long companyId) {
        User user = authService.getCurrentUser();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        // Check if already a member
        if (membershipRepository.findByCompanyIdAndUserId(companyId, user.getId()).isPresent()) {
            throw new RuntimeException("Already a member of this company");
        }

        // Find default "Worker" role
        CompanyRoleEntity workerRole = companyRoleEntityRepository.findByCompanyId(companyId).stream()
                .filter(r -> "Worker".equalsIgnoreCase(r.getName()))
                .findFirst()
                .orElseGet(() -> {
                    // Fallback to any role or create one if none exist? 
                    // Let's at least try to find any role with lowest level if Worker not found
                    return companyRoleEntityRepository.findByCompanyId(companyId).stream()
                            .min((r1, r2) -> Integer.compare(r1.getLevel(), r2.getLevel()))
                            .orElseThrow(() -> new RuntimeException("No roles available in this company"));
                });

        Membership membership = Membership.builder()
                .company(company)
                .user(user)
                .role(workerRole)
                .build();
        
        membershipRepository.save(membership);
        
        // Always use default password for key encryption
        // This ensures that key verification works without requiring password input
        String finalKeyPassword = "defaultPassword123";
        
        // Generate access key for the new member (always with default password)
        org.aldousdev.dockflowbackend.auth.entity.CompanyAccessKey accessKey = 
            digitalSignatureService.generateAccessKey(user, company, finalKeyPassword);
        
        // Create and return key file bytes
        return digitalSignatureService.createKeyFile(accessKey, finalKeyPassword);
    }

    @Override
    @Transactional
    @RequiresRoleLevel(100)
    public UpdateRoleResponse updateRole(Long roleId, UpdateRoleRequest request) {
        User currentUser = authService.getCurrentUser();

        CompanyRoleEntity role = companyRoleEntityRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + roleId));

        // Запрещаем редактировать системные роли
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new BadRequestException("Cannot modify system role");
        }

        // Проверяем, что пользователь состоит в компании этой роли
        Membership membership = membershipRepository.findByCompanyIdAndUserId(
                        role.getCompany().getId(), currentUser.getId())
                .orElseThrow(() -> new ForbiddenException("You do not have access to roles in this company"));

        // Получаем текущий уровень пользователя
        Integer currentUserLevel = membership.getRole().getLevel();

        // Нельзя обновлять роль уровнем выше своего
        if (request.getRoleLevel() > currentUserLevel) {
            throw new ForbiddenException(
                    "Cannot assign role level higher than your own (" + currentUserLevel + ")");
        }

        // Проверка на дубликат имени в компании (исключая текущую роль)
        boolean nameExists = companyRoleEntityRepository.existsByNameAndCompanyIdAndIdNot(
                request.getRoleName(), role.getCompany().getId(), roleId);

        if (nameExists) {
            throw new BadRequestException(
                    "Role with name '" + request.getRoleName() + "' already exists in this company");
        }

        // Обновляем поля
        role.setName(request.getRoleName());
        role.setLevel(request.getRoleLevel());

        CompanyRoleEntity updatedRole = companyRoleEntityRepository.save(role);

        return new UpdateRoleResponse(
                updatedRole.getId(),
                updatedRole.getName(),
                updatedRole.getLevel(),
                updatedRole.getIsSystem()
        );
    }

    @Override
    @Transactional
    @RequiresRoleLevel(100)
    public void deleteRole(Long roleId) {
        User currentUser = authService.getCurrentUser();

        CompanyRoleEntity role = companyRoleEntityRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + roleId));

        // Запрещаем удалять системные роли
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new BadRequestException("Cannot delete system role: " + role.getName());
        }

        // Проверка доступа к компании
        membershipRepository.findByCompanyIdAndUserId(role.getCompany().getId(), currentUser.getId())
                .orElseThrow(() -> new ForbiddenException("You do not have access to this company"));

        // Нельзя удалить роль, если она назначена пользователям
        boolean isAssigned = membershipRepository.existsByRoleId(roleId);
        if (isAssigned) {
            throw new BadRequestException(
                    "Cannot delete role '" + role.getName() + "' because it is assigned to one or more members");
        }

        companyRoleEntityRepository.delete(role);
    }
}
