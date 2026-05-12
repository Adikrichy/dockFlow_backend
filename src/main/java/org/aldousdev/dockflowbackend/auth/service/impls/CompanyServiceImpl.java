package org.aldousdev.dockflowbackend.auth.service.impls;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.auth.components.RequiresRoleLevel;
import org.aldousdev.dockflowbackend.auth.dto.request.AcceptInviteRequest;
import org.aldousdev.dockflowbackend.auth.dto.request.CompanyRequest;
import org.aldousdev.dockflowbackend.auth.dto.request.InviteUserRequest;
import org.aldousdev.dockflowbackend.auth.dto.request.UpdateRoleRequest;
import org.aldousdev.dockflowbackend.auth.dto.response.*;
import org.aldousdev.dockflowbackend.auth.entity.*;
import org.aldousdev.dockflowbackend.auth.enums.InviteChannel;
import org.aldousdev.dockflowbackend.auth.enums.UserType;
import org.aldousdev.dockflowbackend.auth.exceptions.ForbiddenException;
import org.aldousdev.dockflowbackend.auth.exceptions.CompanyAccessDeniedException;
import org.aldousdev.dockflowbackend.auth.exceptions.BadRequestException;
import org.aldousdev.dockflowbackend.auth.exceptions.ResourceNotFoundException;
import org.aldousdev.dockflowbackend.auth.repository.*;
import org.aldousdev.dockflowbackend.auth.security.JWTService;
import org.aldousdev.dockflowbackend.auth.mapper.CompanyMapper;
import org.aldousdev.dockflowbackend.auth.service.CompanyService;
import org.aldousdev.dockflowbackend.auth.service.DigitalSignatureService;
import org.aldousdev.dockflowbackend.auth.service.TelegramProducer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import org.aldousdev.dockflowbackend.workflow.event.WorkflowEventBroadcaster;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyServiceImpl implements CompanyService {
    private final CompanyRepository companyRepository;
    private final CompanyMapper companyMapper;
    private final AuthServiceImpl authService;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final JWTService jwtService;
    private final CompanyRoleEntityRepository companyRoleEntityRepository;
    private final org.aldousdev.dockflowbackend.auth.service.DigitalSignatureService digitalSignatureService;
    private final WorkflowEventBroadcaster workflowEventBroadcaster;
    private final CompanyInviteTokenRepository companyInviteTokenRepository;
    private final EmailServiceImpl emailService;
    private final TelegramProducer telegramProducer;
    private final TelegramBindingRepository telegramBindingRepository;


    @Override
    public CreateCompanyResponse create(CompanyRequest request){
        User currentUser = authService.getCurrentUser();

        Company company = companyMapper.toEntity(request);
        company.setCreatedAt(LocalDateTime.now());
        if (request.getPreferredEditor() == null) {
            company.setPreferredEditor(org.aldousdev.dockflowbackend.document_edit.enums.EditorType.ONLYOFFICE);
        }
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

        // Use password from the request
        String keyPassword = request.getP12Password();
        if (keyPassword == null || keyPassword.isEmpty()) {
            keyPassword = "defaultPassword123"; // Fallback for safety, but UI should enforce it
        }
        
        // Generate access key for the user (always with user password)
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
        claims.put("canViewReports", true);

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
        
        if (currentUser.isAiAssistant()) {
            return listAll();
        }
        
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

        if (!currentUser.isMemberOf(id)) {
            throw new RuntimeException("No access to this company");
        }

//        String roleName = membership.getRole().getName();
//        if(!roleName.equals("CEO") && !roleName.equals("DIRECTOR")){
//            throw new RuntimeException("Access denied: Only CEO or Director can update company");
//        }

//        if(membership.getCompanyRole() != CompanyRole.CEO && membership.getCompanyRole() != CompanyRole.DIRECTOR){
//            throw new RuntimeException("Only Ceo and Director can update this company");
//        }


        System.out.println("Updating company " + id + ". Preferred editor in request: " + request.getPreferredEditor());
        companyMapper.updateCompany(request, company);
        
        if (request.getPreferredEditor() != null) {
            company.setPreferredEditor(request.getPreferredEditor());
            System.out.println("Set company preferred editor to: " + company.getPreferredEditor());
        } else {
            System.out.println("Preferred editor in request was null, skipping update");
        }
        
        companyRepository.save(company);
        System.out.println("Saved company " + id + ". Entity value: " + company.getPreferredEditor());

        return companyMapper.toDto(company);
    }

    @Override
    public void deleteCompany(Long companyId){
        User currentUser = authService.getCurrentUser();
        if (!currentUser.isMemberOf(companyId)) {
            throw new RuntimeException("No access to this company");
        }
        
        // Use repo for administrative role check (AI is not an admin by default)
        Membership membership = membershipRepository.findByCompanyIdAndUserId(companyId,currentUser.getId())
                .orElse(null);
        
        String roleName = membership != null ? membership.getRole().getName() : "MEMBER";
        if(!roleName.equals("CEO") && !roleName.equals("DIRECTOR")){
            throw new RuntimeException("Access denied: Only CEO or Director can update company");
        }

//        if(membership.getCompanyRole() != CompanyRole.CEO){
//            throw new RuntimeException("Only Ceo can delete this company");
//        }

        companyRepository.deleteById(companyId);
    }

    @Override
    public String enterCompany(Long id, byte[] keyFileBytes, String password){
        User user = authService.getCurrentUser();

        Membership membership = membershipRepository.findByCompanyIdAndUserId(
                id,user.getId()).orElseThrow(() -> new CompanyAccessDeniedException("No access to this company"));

        // Verify the key file before granting access using provided user password
        boolean isKeyValid = digitalSignatureService.verifyKeyFile(keyFileBytes, user.getId(), id, password);
        
        if (!isKeyValid) {
            throw new CompanyAccessDeniedException("Invalid key file or password");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", user.getEmail());
        claims.put("userId", user.getId());
        claims.put("userType", user.getUserType().name());
        claims.put("companyRole", membership.getRole().getName());
        claims.put("companyId", membership.getCompany().getId());
        claims.put("companyRoleLevel", membership.getRole().getLevel());
        claims.put("canViewReports", Boolean.TRUE.equals(membership.getRole().getCanViewReports()) || "CEO".equalsIgnoreCase(membership.getRole().getName()));



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
                .canViewReports(true)
                .isSystem(true)
                .company(company)
                .build();

        CompanyRoleEntity director = CompanyRoleEntity.builder()
                .name("Director")
                .level(80)
                .canViewReports(true)
                .isSystem(true)
                .company(company)
                .build();

        CompanyRoleEntity manager = CompanyRoleEntity.builder()
                .name("Manager")
                .level(60)
                .canViewReports(true)
                .isSystem(true)
                .company(company)
                .build();

        CompanyRoleEntity worker = CompanyRoleEntity.builder()
                .name("Worker")
                .level(10)
                .canViewReports(false)
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
                        role.getIsSystem(),
                        role.getCanViewReports()
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

        List<UserResponse> members = membershipRepository.findByCompany(company).stream()
                .map(m -> {
                    User u = m.getUser();
                    return UserResponse.builder()
                            .id(u.getId())
                            .email(u.getEmail())
                            .firstName(u.getFirstName())
                            .lastName(u.getLastName())
                            .companyRole(m.getRole() != null ? m.getRole().getName() : "MEMBER")
                            .build();
                })
                .collect(Collectors.toList());

        // Add AI Assistant to the list if it exists
        userRepository.findByEmail("ai@dockflow.com").ifPresent(ai -> {
            boolean alreadyPresent = members.stream()
                    .anyMatch(m -> m.getEmail().equalsIgnoreCase(ai.getEmail()));
            if (!alreadyPresent) {
                members.add(0, UserResponse.builder()
                        .id(ai.getId())
                        .email(ai.getEmail())
                        .firstName(ai.getFirstName())
                        .lastName(ai.getLastName())
                        .companyRole("AI Assistant")
                        .build());
            }
        });

        return members;
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
        
        // Require password for joining? For now use a more secure approach or throw error
        // since we are moving away from open join anyway. 
        // If we keep join, we should probably pass a password here too.
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

        // Disallow editing system roles
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new BadRequestException("Cannot modify system role");
        }

        Integer userLevel = currentUser.getRoleLevelInCompany(role.getCompany().getId());
        if (userLevel == null) {
            throw new ForbiddenException("You do not have access to roles in this company");
        }

        // Cannot update a role to a level higher than your own
        if (request.getRoleLevel() > userLevel) {
            throw new ForbiddenException(
                    "Cannot assign role level higher than your own (" + userLevel + ")");
        }

        // Check for duplicate name in the company (excluding the current role)
        boolean nameExists = companyRoleEntityRepository.existsByNameAndCompanyIdAndIdNot(
                request.getRoleName(), role.getCompany().getId(), roleId);

        if (nameExists) {
            throw new BadRequestException(
                    "Role with name '" + request.getRoleName() + "' already exists in this company");
        }

        // Update fields
        role.setName(request.getRoleName());
        role.setLevel(request.getRoleLevel());
        role.setCanViewReports(request.getCanViewReports());

        CompanyRoleEntity updatedRole = companyRoleEntityRepository.save(role);

        return new UpdateRoleResponse(
                updatedRole.getId(),
                updatedRole.getName(),
                updatedRole.getLevel(),
                updatedRole.getIsSystem(),
                updatedRole.getCanViewReports()
        );
    }

    @Override
    @Transactional
    @RequiresRoleLevel(100)
    public void deleteRole(Long roleId) {
        User currentUser = authService.getCurrentUser();

        CompanyRoleEntity role = companyRoleEntityRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + roleId));

        // Disallow deleting system roles
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new BadRequestException("Cannot delete system role: " + role.getName());
        }

        if (!currentUser.isMemberOf(role.getCompany().getId())) {
             throw new ForbiddenException("You do not have access to this company");
        }

        // Cannot delete a role if it is assigned to users
        boolean isAssigned = membershipRepository.existsByRoleId(roleId);
        if (isAssigned) {
            throw new BadRequestException(
                    "Cannot delete role '" + role.getName() + "' because it is assigned to one or more members");
        }

        companyRoleEntityRepository.delete(role);
    }

    @Override
    public List<CompanyResponse> listAll() {
        return companyRepository.findAll().stream()
                .map(companyMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<CompanyResponse> searchByName(String name) {
        return companyRepository.findByNameContainingIgnoreCase(name).stream()
                .map(companyMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @RequiresRoleLevel(100)
    public void updateMemberRole(Long userId, Long roleId) {
        // 1. Get current company ID from JWT cookie
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String token = null;
        if (request.getCookies() != null) {
            token = Arrays.stream(request.getCookies())
                    .filter(c -> "jwtWithCompany".equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }

        if (token == null || !jwtService.isTokenValid(token)) {
            throw new CompanyAccessDeniedException("Invalid or missing company context token");
        }

        Long companyId = jwtService.extractCompanyId(token);

        // 2. Load the target membership
        Membership membership = membershipRepository.findByCompanyIdAndUserId(companyId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found in this company"));

        // 3. Load the new role
        CompanyRoleEntity newRole = companyRoleEntityRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));

        // 4. Verify the role belongs to the same company
        if (!newRole.getCompany().getId().equals(companyId)) {
            throw new BadRequestException("Role does not belong to this company");
        }

        // 5. CEO Exclusivity & Integrity Rules
        // Rule: Only one CEO allowed (Level 100). Promoting to 100 is forbidden.
        if (newRole.getLevel() >= 100 && membership.getRole().getLevel() < 100) {
            throw new BadRequestException("Promotion to CEO level (100) is forbidden. There can only be one CEO.");
        }

        // Rule: Current CEO cannot be downgraded via this endpoint.
        if (membership.getRole().getLevel() >= 100 && newRole.getLevel() < 100) {
            throw new BadRequestException("The CEO role cannot be changed. Ownership transfer requires the CEO to leave the company.");
        }

        // 6. Update the membership
        membership.setRole(newRole);
        membershipRepository.save(membership);

        // 7. Notify the user via WebSocket to refresh their token/context
        workflowEventBroadcaster.broadcastRoleUpdated(userId, newRole.getName(), newRole.getLevel());
    }

    @Override
    @Transactional
    @RequiresRoleLevel(100)
    public void inviteUser(Long companyId, InviteUserRequest request){
        User currentUser = authService.getCurrentUser();

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));

        User invitedUser = userRepository.findByEmail(request.getEmail())
                .orElseThrow(()-> new ResourceNotFoundException("User not found"));

        if(membershipRepository.findByCompanyIdAndUserId(companyId, invitedUser.getId()).isPresent()){
            throw new BadRequestException("User is already member of this company");
        }

        companyInviteTokenRepository.deleteByInvitedEmail(request.getEmail());

        CompanyRoleEntity role = companyRoleEntityRepository.findById(request.getRoleId())
                .orElseThrow(()-> new ResourceNotFoundException("Role not found"));

        if(!role.getCompany().getId().equals(companyId)){
            throw new BadRequestException("Role does not belong to this company");
        }

        if(role.getLevel() >= 100){
            throw new BadRequestException("Cannot invite user to CEO role");
        }

        String token = UUID.randomUUID().toString();
        CompanyInviteToken inviteToken = CompanyInviteToken.builder()
                .token(token)
                .invitedBy(currentUser)
                .company(company)
                .invitedEmail(request.getEmail())
                .channel(request.getChannel())
                .assignedRole(role)
                .expiresAt(LocalDateTime.now().plusHours(48))
                .used(false)
                .build();

        companyInviteTokenRepository.save(inviteToken);

        if(request.getChannel() == InviteChannel.EMAIL){
            String link = "http://localhost:5173/accept-invite?token="+token;
            emailService.sendInviteEmail(request.getEmail(),link,company.getName());
        }
        else if(request.getChannel() == InviteChannel.TELEGRAM){
            telegramBindingRepository.findByUserId(invitedUser.getId())
                    .ifPresentOrElse(binding -> {
                        if (binding.getTelegramId() != null) {
                            String link = "http://localhost:5173/accept-invite?token=" + token;
                            String inviteMsg = String.format(
                                "📩 Приглашение в компанию %s\n\n" +
                                "Вас пригласили присоединиться к компании %s на роль %s.\n\n" +
                                "👉 Принять приглашение:\n%s",
                                company.getName(), company.getName(), role.getName(), link);
                            telegramProducer.sendNotification(binding.getTelegramId(), inviteMsg);
                        } else {
                            log.warn("User {} has no telegramLink but Telegram channel was selected", request.getEmail());
                        }
                    }, () -> log.warn("No telegram binding entry for user {}", request.getEmail()));
            log.info("Telegram invite for {} - token: {}", request.getEmail(), token);
        }

        log.info(("Invite sent to {} via {} for company {}"),
                request.getEmail(), request.getChannel(), companyId);
    }

    @Override
    @Transactional
    public byte[] acceptInvite(AcceptInviteRequest request){
        CompanyInviteToken inviteToken = companyInviteTokenRepository.findByToken(request.getToken())
                .orElseThrow(()-> new ResourceNotFoundException("Invite token not found"));

        if(inviteToken.isExpired()){
            companyInviteTokenRepository.delete(inviteToken);
            throw new BadRequestException("Invite token expired");
        }

        if(inviteToken.isUsed()){
            throw new BadRequestException("Invite token is used");
        }

//        User user = userRepository.findByEmail(inviteToken.getInvitedEmail())
//                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        User currentUser = authService.getCurrentUser();
        if(!currentUser.getEmail().equals(inviteToken.getInvitedEmail())) {
            throw new BadRequestException("This invite is not for you");
        }

        User user = currentUser;

        Company company = inviteToken.getCompany();
        Membership membership = Membership.builder()
                .company(company)
                .user(user)
                .role(inviteToken.getAssignedRole())
                .build();
        membershipRepository.save(membership);

        CompanyAccessKey key = digitalSignatureService.generateAccessKey(
                user,company, request.getKeyPassword()
        );

        inviteToken.setUsed(true);
        companyInviteTokenRepository.save(inviteToken);

        log.info("User {} accepted invite to company {}", user.getEmail(), company.getId());

        return digitalSignatureService.createKeyFile(key,request.getKeyPassword());
    }
}
