package org.aldousdev.dockflowbackend.workflow.service.impl;

import lombok.RequiredArgsConstructor;
import org.aldousdev.dockflowbackend.auth.components.RequiresRoleLevel;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.auth.security.JWTService;
import org.aldousdev.dockflowbackend.auth.security.JwtAuthenticationToken;
import org.aldousdev.dockflowbackend.auth.service.impls.AuthServiceImpl;
import org.aldousdev.dockflowbackend.workflow.dto.response.DocumentResponse;
import org.aldousdev.dockflowbackend.workflow.entity.Document;
import org.aldousdev.dockflowbackend.workflow.repository.DocumentRepository;
import org.aldousdev.dockflowbackend.workflow.service.DocumentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {
    private final DocumentRepository documentRepository;
    private final AuthServiceImpl authService;
    private final JWTService jwtService;

    @Value("${file.upload.dir}")
    private String uploadDir;

    @Override
    @RequiresRoleLevel(value = 10, message = "Ony workers can upload document")
    @Transactional
    public DocumentResponse uploadDocument(MultipartFile file) {
        if (!"application/pdf".equals(file.getContentType())) {
            throw new RuntimeException("Unsupported file type");
        }

        User currentUser = authService.getCurrentUser();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            throw new RuntimeException("Invalid authentication token type");
        }
        String token = jwtAuth.getToken();
        Long companyId = jwtService.extractCompanyId(token);

        if(companyId == null){
            throw new RuntimeException("Company not found");
        }

        try{
            Path companyDir = Paths.get(uploadDir,"company-" + companyId);
            Files.createDirectories(companyDir);

            String fileName = LocalDateTime.now().toString().replace(":","-")+"_" + file.getOriginalFilename();
            Path filePath = companyDir.resolve(fileName);

            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            Document document = Document.builder()
                    .originalFilename(file.getOriginalFilename())
                    .filePath(filePath.toString())
                    .fileSize(file.getSize())
                    .company(currentUser.getMemberships().stream()
                            .filter(m -> m.getCompany().getId().equals(companyId))
                            .findFirst()
                            .get()
                            .getCompany())
                    .uploadedBy(currentUser)
                    .signed(false)
                    .build();

            document = documentRepository.save(document);

            return DocumentResponse.builder()
                    .id(document.getId())
                    .originalFilename(document.getOriginalFilename())
                    .filePath(document.getFilePath())
                    .fileSize(document.getFileSize())
                    .uploadedAt(document.getUploadedAt())
                    .uploadedBy(currentUser.getFirstName() + " " + currentUser.getLastName())
                    .signed(document.getSigned())
                    .build();
        }
        catch(IOException exception){
            throw new RuntimeException("Error in save file"+exception);
        }
    }
}
