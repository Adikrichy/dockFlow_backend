package org.aldousdev.dockflowbackend.workflow.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.workflow.entity.Document;
import org.aldousdev.dockflowbackend.workflow.entity.DocumentVersion;
import org.aldousdev.dockflowbackend.workflow.repository.DocumentRepository;
import org.aldousdev.dockflowbackend.workflow.repository.DocumentVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Сервис для управления версиями документов
 * Обеспечивает создание, хранение и получение версий документов
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentVersioningService {

    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentRepository documentRepository;
    private final DocumentHashService documentHashService;
    private final DocumentPdfService documentPdfService;

    /**
     * Создает новую версию документа
     */
    @Transactional
    public DocumentVersion createNewVersion(Document document, MultipartFile file,
                                          User createdBy, String changeDescription,
                                          String changeType) throws IOException {

        log.info("Creating new version for document {} by user {}", document.getId(), createdBy.getEmail());

        // Получаем номер следующей версии
        Integer nextVersionNumber = getNextVersionNumber(document.getId());

        // Создаем директорию для версий
        Path versionDir = Paths.get(document.getFilePath()).getParent().resolve("versions");
        Files.createDirectories(versionDir);

        // Генерируем имя файла для версии
        String versionFileName = String.format("%s_v%d_%s",
                document.getOriginalFilename(),
                nextVersionNumber,
                LocalDateTime.now().toString().replace(":", "-").replace(".", "-"));
        Path versionFilePath = versionDir.resolve(versionFileName);

        // Копируем файл
        Files.copy(file.getInputStream(), versionFilePath);

        // Вычисляем хеш
        String sha256Hash = documentHashService.calculateSha256Hash(file.getBytes());

        // Проверяем дубликаты
        if (documentHashService.isDuplicate(sha256Hash, document.getCompany().getId())) {
            log.warn("Duplicate file detected for document {} in company {}",
                    document.getId(), document.getCompany().getId());
            // Можно либо выбросить исключение, либо продолжить - зависит от бизнес-логики
        }

        // Создаем новую версию
        DocumentVersion newVersion = DocumentVersion.builder()
                .document(document)
                .versionNumber(nextVersionNumber)
                .filePath(versionFilePath.toString())
                .originalFilename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .sha256Hash(sha256Hash)
                .changeDescription(changeDescription)
                .changeType(changeType)
                .createdBy(createdBy)
                .isCurrent(true)
                .build();

        // Сохраняем версию
        newVersion = documentVersionRepository.save(newVersion);

        // Обновляем текущую версию документа
        updateCurrentVersion(document, newVersion);

        log.info("Created version {} for document {}", nextVersionNumber, document.getId());
        return newVersion;
    }

    @Transactional
    public DocumentVersion createNewVersionFromFile(
            Document document,
            Path sourceFilePath,
            String originalFilename,
            String contentType,
            User createdBy,
            String changeDescription,
            String changeType
    ) throws IOException {

        log.info("Creating new version (from file) for document {} by user {}", document.getId(), createdBy.getEmail());

        Integer nextVersionNumber = getNextVersionNumber(document.getId());

        Path versionDir = Paths.get(document.getFilePath()).getParent().resolve("versions");
        Files.createDirectories(versionDir);

        String versionFileName = String.format("%s_v%d_%s",
                document.getOriginalFilename(),
                nextVersionNumber,
                LocalDateTime.now().toString().replace(":", "-").replace(".", "-"));
        Path versionFilePath = versionDir.resolve(versionFileName);

        Files.copy(sourceFilePath, versionFilePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        byte[] bytes = Files.readAllBytes(versionFilePath);
        String sha256Hash = documentHashService.calculateSha256Hash(bytes);

        DocumentVersion newVersion = DocumentVersion.builder()
                .document(document)
                .versionNumber(nextVersionNumber)
                .filePath(versionFilePath.toString())
                .originalFilename(originalFilename)
                .contentType(contentType)
                .fileSize(Files.size(versionFilePath))
                .sha256Hash(sha256Hash)
                .changeDescription(changeDescription)
                .changeType(changeType)
                .createdBy(createdBy)
                .isCurrent(true)
                .build();

        newVersion = documentVersionRepository.save(newVersion);
        updateCurrentVersion(document, newVersion);

        log.info("Created version {} for document {}", nextVersionNumber, document.getId());
        return newVersion;
    }

    @Transactional
    public DocumentVersion createNewVersionFromBytes(
            Document document,
            byte[] fileBytes,
            String originalFilename,
            String contentType,
            User createdBy,
            String changeDescription,
            String changeType
    ) throws IOException {

        log.info("Creating new version (from bytes) for document {} by user {}", document.getId(), createdBy.getEmail());

        Integer nextVersionNumber = getNextVersionNumber(document.getId());

        Path versionDir = Paths.get(document.getFilePath()).getParent().resolve("versions");
        Files.createDirectories(versionDir);

        String versionFileName = String.format("%s_v%d_%s",
                document.getOriginalFilename(),
                nextVersionNumber,
                LocalDateTime.now().toString().replace(":", "-").replace(".", "-"));
        Path versionFilePath = versionDir.resolve(versionFileName);

        Files.write(versionFilePath, fileBytes);

        String sha256Hash = documentHashService.calculateSha256Hash(fileBytes);

        DocumentVersion newVersion = DocumentVersion.builder()
                .document(document)
                .versionNumber(nextVersionNumber)
                .filePath(versionFilePath.toString())
                .originalFilename(originalFilename)
                .contentType(contentType)
                .fileSize((long) fileBytes.length)
                .sha256Hash(sha256Hash)
                .changeDescription(changeDescription)
                .changeType(changeType)
                .createdBy(createdBy)
                .isCurrent(true)
                .build();

        newVersion = documentVersionRepository.save(newVersion);
        updateCurrentVersion(document, newVersion);

        log.info("Created version {} for document {}", nextVersionNumber, document.getId());
        return newVersion;
    }

    /**
     * Создает версию с watermark
     */
    @Transactional
    public DocumentVersion createWatermarkedVersion(Document document, String watermarkText,
                                                   User createdBy) throws IOException {

        log.info("Creating watermarked version for document {} by user {}", document.getId(), createdBy.getEmail());

        // Получаем текущую версию
        DocumentVersion currentVersion = getCurrentVersion(document.getId())
                .orElseThrow(() -> new RuntimeException("No current version found"));

        // Читаем файл текущей версии
        byte[] fileContent = Files.readAllBytes(Paths.get(currentVersion.getFilePath()));

        // Добавляем watermark
        byte[] watermarkedContent = documentPdfService.addWatermark(fileContent, watermarkText);

        // Создаем новую версию с watermark
        Integer nextVersionNumber = getNextVersionNumber(document.getId());
        Path versionDir = Paths.get(document.getFilePath()).getParent().resolve("versions");
        Files.createDirectories(versionDir);

        String versionFileName = String.format("%s_v%d_watermarked_%s",
                document.getOriginalFilename(),
                nextVersionNumber,
                LocalDateTime.now().toString().replace(":", "-"));
        Path versionFilePath = versionDir.resolve(versionFileName);

        // Сохраняем файл с watermark
        Files.write(versionFilePath, watermarkedContent);

        // Вычисляем новый хеш
        String sha256Hash = documentHashService.calculateSha256Hash(watermarkedContent);

        DocumentVersion watermarkedVersion = DocumentVersion.builder()
                .document(document)
                .versionNumber(nextVersionNumber)
                .filePath(versionFilePath.toString())
                .originalFilename(document.getOriginalFilename())
                .contentType("application/pdf")
                .fileSize((long) watermarkedContent.length)
                .sha256Hash(sha256Hash)
                .changeDescription("Added watermark: " + watermarkText)
                .changeType("WATERMARK")
                .createdBy(createdBy)
                .hasWatermark(true)
                .isCurrent(true)
                .build();

        watermarkedVersion = documentVersionRepository.save(watermarkedVersion);
        updateCurrentVersion(document, watermarkedVersion);

        log.info("Created watermarked version {} for document {}", nextVersionNumber, document.getId());
        return watermarkedVersion;
    }

    /**
     * Создает подписанную версию документа
     */
    @Transactional
    public DocumentVersion createSignedVersion(Document document, String signatureText,
                                             String signerName, User createdBy) throws IOException {

        log.info("Creating signed version for document {} by user {}", document.getId(), createdBy.getEmail());

        // Получаем текущую версию
        DocumentVersion currentVersion = getCurrentVersion(document.getId())
                .orElseThrow(() -> new RuntimeException("No current version found"));

        // Читаем файл текущей версии
        byte[] fileContent = Files.readAllBytes(Paths.get(currentVersion.getFilePath()));

        // Добавляем подпись
        byte[] signedContent = documentPdfService.addDigitalSignature(fileContent, signatureText, signerName);

        // Создаем новую версию с подписью
        Integer nextVersionNumber = getNextVersionNumber(document.getId());
        Path versionDir = Paths.get(document.getFilePath()).getParent().resolve("versions");
        Files.createDirectories(versionDir);

        String versionFileName = String.format("%s_v%d_signed_%s",
                document.getOriginalFilename(),
                nextVersionNumber,
                LocalDateTime.now().toString().replace(":", "-"));
        Path versionFilePath = versionDir.resolve(versionFileName);

        // Сохраняем подписанный файл
        Files.write(versionFilePath, signedContent);

        // Вычисляем новый хеш
        String sha256Hash = documentHashService.calculateSha256Hash(signedContent);

        DocumentVersion signedVersion = DocumentVersion.builder()
                .document(document)
                .versionNumber(nextVersionNumber)
                .filePath(versionFilePath.toString())
                .originalFilename(document.getOriginalFilename())
                .contentType("application/pdf")
                .fileSize((long) signedContent.length)
                .sha256Hash(sha256Hash)
                .changeDescription("Signed by: " + signerName)
                .changeType("SIGN")
                .createdBy(createdBy)
                .isSigned(true)
                .isCurrent(true)
                .build();

        signedVersion = documentVersionRepository.save(signedVersion);
        updateCurrentVersion(document, signedVersion);

        // Обновляем статус документа
        document.setSigned(true);
        documentRepository.save(document);

        log.info("Created signed version {} for document {}", nextVersionNumber, document.getId());
        return signedVersion;
    }

    /**
     * Получает все версии документа
     */
    public List<DocumentVersion> getDocumentVersions(Long documentId) {
        return documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId);
    }

    /**
     * Получает текущую версию документа
     */
    public Optional<DocumentVersion> getCurrentVersion(Long documentId) {
        return documentVersionRepository.findCurrentVersionByDocumentId(documentId);
    }

    /**
     * Получает конкретную версию документа
     */
    public Optional<DocumentVersion> getVersion(Long documentId, Integer versionNumber) {
        return documentVersionRepository.findByDocumentIdAndVersionNumber(documentId, versionNumber);
    }

    /**
     * Получает количество версий документа
     */
    public long getVersionCount(Long documentId) {
        return documentVersionRepository.countByDocumentId(documentId);
    }

    /**
     * Получает все подписанные версии документа
     */
    public List<DocumentVersion> getSignedVersions(Long documentId) {
        return documentVersionRepository.findSignedVersionsByDocumentId(documentId);
    }

    /**
     * Восстанавливает предыдущую версию как текущую
     */
    @Transactional
    public void restoreVersion(Long documentId, Integer versionNumber, User restoredBy) {
        log.info("Restoring version {} for document {} by user {}", versionNumber, documentId, restoredBy.getEmail());

        DocumentVersion versionToRestore = getVersion(documentId, versionNumber)
                .orElseThrow(() -> new RuntimeException("Version not found"));

        // Снимаем флаг текущей версии со всех версий
        List<DocumentVersion> allVersions = getDocumentVersions(documentId);
        allVersions.forEach(v -> v.setIsCurrent(false));
        documentVersionRepository.saveAll(allVersions);

        // Устанавливаем выбранную версию как текущую
        versionToRestore.setIsCurrent(true);
        documentVersionRepository.save(versionToRestore);

        // ОБНОВЛЕНИЕ ОСНОВНОГО ДОКУМЕНТА
        Document document = versionToRestore.getDocument();
        // Важно: копируем путь и метаданные обратно в основной документ, чтобы скачивание работало корректно
        document.setFilePath(versionToRestore.getFilePath());
        document.setContentType(versionToRestore.getContentType());
        document.setFileSize(versionToRestore.getFileSize());
        // Если бы у нас было поле hash в документе, мы бы и его обновили.
        // document.setSha256Hash(versionToRestore.getSha256Hash());
        
        documentRepository.save(document);

        log.info("Restored version {} as current for document {}", versionNumber, documentId);
    }

    private Integer getNextVersionNumber(Long documentId) {
        return documentVersionRepository.findMaxVersionNumberByDocumentId(documentId)
                .map(max -> max + 1)
                .orElse(1);
    }

    private void updateCurrentVersion(Document document, DocumentVersion newCurrentVersion) {
        // Снимаем флаг текущей версии со всех предыдущих версий
        List<DocumentVersion> existingVersions = documentVersionRepository
                .findByDocumentIdOrderByVersionNumberDesc(document.getId());

        existingVersions.stream()
                .filter(v -> !v.getId().equals(newCurrentVersion.getId()))
                .forEach(v -> v.setIsCurrent(false));

        documentVersionRepository.saveAll(existingVersions);

        // Update the main document to point to the new version's file
        // This ensures that "Edit" and "Download" always use the latest version
        document.setFilePath(newCurrentVersion.getFilePath());
        document.setFileSize(newCurrentVersion.getFileSize());
        document.setContentType(newCurrentVersion.getContentType());
        documentRepository.save(document);
    }
}
