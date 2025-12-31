package org.aldousdev.dockflowbackend.auth.service.impls;

import lombok.RequiredArgsConstructor;
import org.aldousdev.dockflowbackend.auth.components.RequiresRoleLevel;
import org.aldousdev.dockflowbackend.auth.dto.request.CompanyRequest;
import org.aldousdev.dockflowbackend.auth.dto.response.CompanyResponse;
import org.aldousdev.dockflowbackend.auth.dto.response.CreateCompanyResponse;
import org.aldousdev.dockflowbackend.auth.dto.response.CreateRoleResponse;
import org.aldousdev.dockflowbackend.auth.dto.response.UserResponse;
import org.aldousdev.dockflowbackend.auth.entity.Company;
import org.aldousdev.dockflowbackend.auth.entity.CompanyRoleEntity;
import org.aldousdev.dockflowbackend.auth.entity.Membership;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.auth.enums.UserType;
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
    public String enterCompany(Long id){
        User user = authService.getCurrentUser();

        Membership membership = membershipRepository.findByCompanyIdAndUserId(
                id,user.getId()).orElseThrow(() -> new RuntimeException("No access to this company"));

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
    public void joinCompany(Long companyId) {
        User user = authService.getCurrentUser();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        // Check if already a member
        if (membershipRepository.findByCompanyIdAndUserId(companyId, user.getId()).isPresent()) {
            return; // Already joined
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
    }
}
