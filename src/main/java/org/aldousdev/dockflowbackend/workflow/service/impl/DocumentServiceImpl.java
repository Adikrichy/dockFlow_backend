package org.aldousdev.dockflowbackend.workflow.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.auth.components.RequiresRoleLevel;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.auth.exceptions.ResourceNotFoundException;
import org.aldousdev.dockflowbackend.auth.exceptions.CompanyNotFoundException;
import org.aldousdev.dockflowbackend.workflow.entity.DocumentVersion;
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

        String contentType = file.getContentType();
        boolean isPdf = "application/pdf".equalsIgnoreCase(contentType);
        boolean isDocx = "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equalsIgnoreCase(contentType)
                || (file.getOriginalFilename() != null && file.getOriginalFilename().toLowerCase().endsWith(".docx"));

        if (!isPdf && !isDocx) {
            log.warn("Attempted to upload unsupported file: {}", contentType);
            throw new InvalidFileException("Only PDF and DOCX files are supported. Received: " + contentType);
        }

        User currentUser = authService.getCurrentUser();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            log.error("Invalid authentication token type for user: {}", currentUser.getEmail());
            throw new DocumentUploadException("Invalid authentication. Please re-login to your company.");
        }
        String token = jwtAuth.getToken();
        Long companyId = jwtService.extractCompanyId(token);

        if(companyId == null){
            log.error("Company ID not found in JWT token for user: {}", currentUser.getEmail());
            throw new DocumentUploadException("No active company context. Please select a company first.");
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

            // Check duplicates before document creation
            String sha256Hash = documentHashService.calculateSha256Hash(file.getBytes());
            boolean isDuplicate = documentHashService.isDuplicate(sha256Hash, companyId);

            if (isDuplicate) {
                log.warn("Duplicate document detected for company {}", companyId);
                // Can either throw exception or continue - depends on business logic
                // throw new DocumentUploadException("Document with identical content already exists");
            }

            Document document = Document.builder()
                    .originalFilename(file.getOriginalFilename() != null ? file.getOriginalFilename() : "Untitled Document")
                    .filePath(filePath.toString())
                    .contentType(isPdf ? "application/pdf" : "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                    .fileSize(file.getSize())
                    .company(company)
                    .uploadedBy(currentUser)
                    .signed(false)
                    .build();

            document = documentRepository.save(document);

            // Create the first version of the document
            try {
                documentVersioningService.createNewVersion(document, file, currentUser,
                        "Initial document upload", "UPLOAD");
                log.info("Created initial version for document {}", document.getId());
            } catch (Exception e) {
                log.error("Failed to create initial version for document {}", document.getId(), e);
                // Do not throw exception - document is created, just without versions
            }
            log.info("Document successfully uploaded. ID: {}, Company: {}, User: {}", 
                    document.getId(), companyId, currentUser.getEmail());

            return DocumentResponse.builder()
                    .id(document.getId())
                    .originalFilename(document.getOriginalFilename())
                    .contentType(document.getContentType())
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
                .contentType(document.getContentType())
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
        
        return getResource(document.getFilePath());
    }

    @Override
    @Transactional(readOnly = true)
    public Resource downloadDocumentVersion(Long documentId, Integer versionNumber) {
        log.info("Downloading document {} version {}", documentId, versionNumber);
        DocumentVersion version = documentVersioningService.getVersion(documentId, versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Version not found: " + versionNumber));

        return getResource(version.getFilePath());
    }

    private Resource getResource(String filePath) {
        try {
            Path path = Paths.get(filePath);
            Resource resource = new UrlResource(path.toUri());

            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                log.error("File not found or not readable: {}", filePath);
                throw new RuntimeException("Could not read file: " + filePath);
            }
        } catch (MalformedURLException e) {
            log.error("Invalid file path: {}", filePath, e);
            throw new RuntimeException("Error: " + e.getMessage());
        }
    }
}
