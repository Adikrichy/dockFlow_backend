package org.aldousdev.dockflowbackend.service.impls;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.aldousdev.dockflowbackend.auth.dto.request.CompanyRequest;
import org.aldousdev.dockflowbackend.auth.dto.response.CompanyResponse;
import org.aldousdev.dockflowbackend.auth.dto.response.CreateCompanyResponse;
import org.aldousdev.dockflowbackend.auth.entity.Company;
import org.aldousdev.dockflowbackend.auth.entity.Membership;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.auth.enums.CompanyRole;
import org.aldousdev.dockflowbackend.auth.enums.UserType;
import org.aldousdev.dockflowbackend.auth.repository.CompanyRepository;
import org.aldousdev.dockflowbackend.auth.repository.MembershipRepository;
import org.aldousdev.dockflowbackend.auth.repository.UserRepository;
import org.aldousdev.dockflowbackend.auth.security.JWTService;
import org.aldousdev.dockflowbackend.mapper.CompanyMapper;
import org.aldousdev.dockflowbackend.service.CompanyService;
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

    @Override
    public CreateCompanyResponse create(CompanyRequest request){
        User currentUser = authService.getCurrentUser();

        Company company = companyMapper.toEntity(request);
        company.setCreatedAt(LocalDateTime.now());
        company = companyRepository.save(company);

        Membership membership = new Membership();
        membership.setCompany(company);
        membership.setUser(currentUser);
        membership.setCompanyRole(CompanyRole.CEO);
        membershipRepository.save(membership);

        currentUser.setUserType(UserType.COMPANY_OWNER);
        userRepository.save(currentUser);

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", currentUser.getEmail());
        claims.put("userId", currentUser.getId());
        claims.put("userType", currentUser.getUserType().name());
        claims.put("companyRole", membership.getCompanyRole().name());

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
    public CompanyResponse updateCompany(Long id,CompanyRequest request, String token){
        if(token == null || !jwtService.isTokenValid(token)){
            throw new RuntimeException("Invalid token");
        }

        String role = jwtService.extractCompanyRole(token);
        if(!"CEO".equals(role) && !"DIRECTOR".equals(role)){
            throw new RuntimeException("Access denied: only CEO or Director can update company");
        }


        Company company = companyRepository.findById(id)
                .orElseThrow(()-> new RuntimeException("Company not found"));

        User currentUser = authService.getCurrentUser();

        Membership membership = membershipRepository.findByCompanyIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new RuntimeException("No access to this company"));
        if(membership.getCompanyRole() != CompanyRole.CEO && membership.getCompanyRole() != CompanyRole.DIRECTOR){
            throw new RuntimeException("Only Ceo and Director can update this company");
        }


        companyMapper.updateCompany(request, company);

        return companyMapper.toDto(company);
    }

    @Override
    public void deleteCompany(Long companyId){
        User currentUser = authService.getCurrentUser();
        Membership membership = membershipRepository.findByCompanyIdAndUserId(companyId,currentUser.getId())
                .orElseThrow(() -> new RuntimeException("No access to this company"));

        if(membership.getCompanyRole() != CompanyRole.CEO){
            throw new RuntimeException("Only Ceo can delete this company");
        }

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
        claims.put("companyRole", membership.getCompanyRole().name());


        return jwtService.generateCompanyToken(
                 user, claims);
    }

    @Override
    public String leaveCompany(){
        User user = authService.getCurrentUser();
        return jwtService.generateCompanyToken(user, null);
    }

}
