package org.aldousdev.dockflowbackend.workflow.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.auth.components.RequiresRoleLevel;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.auth.exceptions.ResourceNotFoundException;
import org.aldousdev.dockflowbackend.auth.exceptions.CompanyNotFoundException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import java.net.MalformedURLException;
import org.aldousdev.dockflowbackend.auth.repository.CompanyRepository;
import org.aldousdev.dockflowbackend.auth.security.JWTService;
import org.aldousdev.dockflowbackend.auth.security.JwtAuthenticationToken;
import org.aldousdev.dockflowbackend.auth.service.impls.AuthServiceImpl;
import org.aldousdev.dockflowbackend.workflow.dto.response.DocumentResponse;
import org.aldousdev.dockflowbackend.workflow.entity.Document;
import org.aldousdev.dockflowbackend.workflow.exceptions.DocumentUploadException;
import org.aldousdev.dockflowbackend.workflow.exceptions.InvalidFileException;
import org.aldousdev.dockflowbackend.workflow.repository.DocumentRepository;
import org.aldousdev.dockflowbackend.workflow.service.DocumentHashService;
import org.aldousdev.dockflowbackend.workflow.service.DocumentService;
import org.aldousdev.dockflowbackend.workflow.service.DocumentVersioningService;
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
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentServiceImpl implements DocumentService {
    private final DocumentRepository documentRepository;
    private final CompanyRepository companyRepository;
    private final AuthServiceImpl authService;
    private final JWTService jwtService;
    private final DocumentVersioningService documentVersioningService;
    private final DocumentHashService documentHashService;

    @Value("${file.upload.dir}")
    private String uploadDir;

    @Override
    @RequiresRoleLevel(value = 10, message = "Only workers and above can upload document")
    @Transactional
    public DocumentResponse uploadDocument(MultipartFile file) {
        log.info("Upload document started for user: {}", authService.getCurrentUser().getEmail());

        if (file == null || file.isEmpty()) {
            log.warn("Attempted to upload empty file");
            throw new InvalidFileException("File cannot be empty");
        }

        if (!"application/pdf".equals(file.getContentType())) {
            log.warn("Attempted to upload non-PDF file: {}", file.getContentType());
            throw new InvalidFileException("Only PDF files are supported. Received: " + file.getContentType());
        }

        User currentUser = authService.getCurrentUser();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            log.error("Invalid authentication token type for user: {}", currentUser.getEmail());
            throw new RuntimeException("Invalid authentication token type");
        }
        String token = jwtAuth.getToken();
        Long companyId = jwtService.extractCompanyId(token);

        if(companyId == null){
            log.error("Company ID not found in JWT token for user: {}", currentUser.getEmail());
            throw new RuntimeException("Company not found in token");
        }

        try{
            Path companyDir = Paths.get(uploadDir,"company-" + companyId);
            Files.createDirectories(companyDir);

            String fileName = LocalDateTime.now().toString().replace(":","-")+"_" + file.getOriginalFilename();
            Path filePath = companyDir.resolve(fileName);

            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            log.debug("File saved to: {}", filePath);

            var company = companyRepository.findById(companyId)
                    .orElseThrow(() -> new CompanyNotFoundException(
                        "Company not found with id: " + companyId));

            // Проверяем дубликаты перед созданием документа
            String sha256Hash = documentHashService.calculateSha256Hash(file.getBytes());
            boolean isDuplicate = documentHashService.isDuplicate(sha256Hash, companyId);

            if (isDuplicate) {
                log.warn("Duplicate document detected for company {}", companyId);
                // Можно либо выбросить исключение, либо продолжить - зависит от бизнес-логики
                // throw new DocumentUploadException("Document with identical content already exists");
            }

            Document document = Document.builder()
                    .originalFilename(file.getOriginalFilename())
                    .filePath(filePath.toString())
                    .fileSize(file.getSize())
                    .company(company)
                    .uploadedBy(currentUser)
                    .signed(false)
                    .build();

            document = documentRepository.save(document);

            // Создаем первую версию документа
            try {
                documentVersioningService.createNewVersion(document, file, currentUser,
                        "Initial document upload", "UPLOAD");
                log.info("Created initial version for document {}", document.getId());
            } catch (Exception e) {
                log.error("Failed to create initial version for document {}", document.getId(), e);
                // Не выбрасываем исключение - документ создан, просто без версий
            }
            log.info("Document successfully uploaded. ID: {}, Company: {}, User: {}", 
                    document.getId(), companyId, currentUser.getEmail());

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
            log.error("IO error during file upload for user: {}", currentUser.getEmail(), exception);
            throw new DocumentUploadException("Error saving file: " + exception.getMessage(), exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentResponse getDocument(Long id) {
        log.debug("Fetching document details for ID: {}", id);
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + id));
        return mapToResponse(document);
    }

    @Override
    @Transactional(readOnly = true)
    public Document getDocumentEntity(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getCompanyDocuments(Long companyId) {
        log.debug("Fetching documents for company ID: {}", companyId);
        return documentRepository.findByCompanyId(companyId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private DocumentResponse mapToResponse(Document document) {
        return DocumentResponse.builder()
                .id(document.getId())
                .originalFilename(document.getOriginalFilename())
                .filePath(document.getFilePath())
                .fileSize(document.getFileSize())
                .uploadedAt(document.getUploadedAt())
                .uploadedBy(document.getUploadedBy().getFirstName() + " " + document.getUploadedBy().getLastName())
                .signed(document.getSigned())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Resource downloadDocument(Long id) {
        log.info("Downloading document: {}", id);
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + id));
        
        try {
            Path path = Paths.get(document.getFilePath());
            Resource resource = new UrlResource(path.toUri());
            
            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                log.error("File not found or not readable: {}", document.getFilePath());
                throw new RuntimeException("Could not read file: " + document.getFilePath());
            }
        } catch (MalformedURLException e) {
            log.error("Invalid file path: {}", document.getFilePath(), e);
            throw new RuntimeException("Error: " + e.getMessage());
        }
    }
}
