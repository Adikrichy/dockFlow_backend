package org.aldousdev.dockflowbackend.workflow.service;

import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.workflow.entity.DocumentVersion;
import org.aldousdev.dockflowbackend.workflow.repository.DocumentVersionRepository;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;

/**
 * Сервис для работы с SHA-256 хешами документов
 * Предотвращает загрузку дубликатов
 */
@Service
@Slf4j
public class DocumentHashService {

    private final DocumentVersionRepository documentVersionRepository;

    public DocumentHashService(DocumentVersionRepository documentVersionRepository) {
        this.documentVersionRepository = documentVersionRepository;
    }

    /**
     * Вычисляет SHA-256 хеш файла
     */
    public String calculateSha256Hash(byte[] fileContent) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(fileContent);
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            throw new RuntimeException("Hash calculation failed", e);
        }
    }

    /**
     * Вычисляет SHA-256 хеш файла из массива байтов
     */
    public String calculateSha256Hash(java.io.ByteArrayInputStream inputStream) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            byte[] hashBytes = digest.digest();
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (Exception e) {
            log.error("Error calculating SHA-256 hash", e);
            throw new RuntimeException("Hash calculation failed", e);
        }
    }

    /**
     * Проверяет, существует ли документ с таким же хешем в компании
     */
    public boolean isDuplicate(String sha256Hash, Long companyId) {
        Optional<DocumentVersion> existingVersion = documentVersionRepository
                .findBySha256HashAndCompanyId(sha256Hash, companyId);

        if (existingVersion.isPresent()) {
            log.info("Found duplicate document with hash {} in company {}", sha256Hash, companyId);
            return true;
        }

        return false;
    }

    /**
     * Находит существующий документ по хешу в компании
     */
    public Optional<DocumentVersion> findDuplicate(String sha256Hash, Long companyId) {
        return documentVersionRepository.findBySha256HashAndCompanyId(sha256Hash, companyId);
    }

    /**
     * Проверяет целостность файла по хешу
     */
    public boolean verifyFileIntegrity(byte[] fileContent, String expectedHash) {
        String actualHash = calculateSha256Hash(fileContent);
        boolean isValid = actualHash.equals(expectedHash);

        if (!isValid) {
            log.warn("File integrity check failed. Expected: {}, Actual: {}", expectedHash, actualHash);
        }

        return isValid;
    }

    /**
     * Проверяет целостность файла по хешу из input stream
     */
    public boolean verifyFileIntegrity(java.io.ByteArrayInputStream inputStream, String expectedHash) {
        String actualHash = calculateSha256Hash(inputStream);
        boolean isValid = actualHash.equals(expectedHash);

        if (!isValid) {
            log.warn("File integrity check failed. Expected: {}, Actual: {}", expectedHash, actualHash);
        }

        return isValid;
    }
}
