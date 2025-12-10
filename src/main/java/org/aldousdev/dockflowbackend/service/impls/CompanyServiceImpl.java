package org.aldousdev.dockflowbackend.service.impls;

import lombok.RequiredArgsConstructor;
import org.aldousdev.dockflowbackend.auth.dto.request.CompanyRequest;
import org.aldousdev.dockflowbackend.auth.dto.response.CompanyResponse;
import org.aldousdev.dockflowbackend.auth.entity.Company;
import org.aldousdev.dockflowbackend.auth.entity.Membership;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.auth.enums.CompanyRole;
import org.aldousdev.dockflowbackend.auth.repository.CompanyRepository;
import org.aldousdev.dockflowbackend.auth.repository.MembershipRepository;
import org.aldousdev.dockflowbackend.mapper.CompanyMapper;
import org.aldousdev.dockflowbackend.service.CompanyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CompanyServiceImpl implements CompanyService {
    private final CompanyRepository companyRepository;
    private final CompanyMapper companyMapper;
    private final AuthServiceImpl authService;
    private final MembershipRepository membershipRepository;

    @Override
    public CompanyResponse create(CompanyRequest request){
        User currentUser = authService.getCurrentUser();

        Company company = companyMapper.toEntity(request);
        company = companyRepository.save(company);

        Membership membership = new Membership();
        membership.setCompany(company);
        membership.setUser(currentUser);
        membership.setCompanyRole(CompanyRole.CEO);
        membershipRepository.save(membership);

        return companyMapper.toDto(company);

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
    public CompanyResponse updateCompany(Long id, CompanyRequest request){
        User currentUser = authService.getCurrentUser();

        Membership membership = membershipRepository.findByCompanyIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new RuntimeException("No access to this company"));
        if(membership.getCompanyRole() != CompanyRole.CEO){
            throw new RuntimeException("Only Ceo can update this company");
        }

        Company company = membership.getCompany();
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
}
