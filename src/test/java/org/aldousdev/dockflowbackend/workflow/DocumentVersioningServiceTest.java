package org.aldousdev.dockflowbackend.workflow;

import org.aldousdev.dockflowbackend.auth.entity.Company;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.workflow.entity.Document;
import org.aldousdev.dockflowbackend.workflow.entity.DocumentVersion;
import org.aldousdev.dockflowbackend.workflow.repository.DocumentVersionRepository;
import org.aldousdev.dockflowbackend.workflow.service.DocumentHashService;
import org.aldousdev.dockflowbackend.workflow.service.DocumentPdfService;
import org.aldousdev.dockflowbackend.workflow.service.DocumentVersioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class DocumentVersioningServiceTest {

    @Mock
    private DocumentVersionRepository documentVersionRepository;

    @Mock
    private DocumentHashService documentHashService;

    @Mock
    private DocumentPdfService documentPdfService;

    private DocumentVersioningService documentVersioningService;

    private Document testDocument;
    private User testUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        documentVersioningService = new DocumentVersioningService(
            documentVersionRepository, null, documentHashService, documentPdfService);

        testUser = User.builder()
            .id(1L)
            .email("test@example.com")
            .firstName("Test")
            .lastName("User")
            .build();

        Company testCompany = Company.builder()
            .id(1L)
            .name("Test Company")
            .build();

        testDocument = Document.builder()
            .id(1L)
            .originalFilename("test.pdf")
            .filePath("./uploads/company-1/test.pdf")
            .company(testCompany)
            .amount(BigDecimal.valueOf(10000))
            .build();
    }

    @Test
    void testGetDocumentVersions() {
        // Given
        when(documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(1L))
            .thenReturn(java.util.List.of());

        // When
        var versions = documentVersioningService.getDocumentVersions(1L);

        // Then
        assertThat(versions).isEmpty();
    }

    @Test
    void testGetDocumentVersions() {
        // Given
        when(documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(1L))
            .thenReturn(java.util.List.of());

        // When
        var versions = documentVersioningService.getDocumentVersions(1L);

        // Then
        assertThat(versions).isEmpty();
    }

    @Test
    void testGetCurrentVersion() {
        // Given
        DocumentVersion currentVersion = DocumentVersion.builder()
            .id(1L)
            .versionNumber(2)
            .isCurrent(true)
            .build();

        when(documentVersionRepository.findCurrentVersionByDocumentId(1L))
            .thenReturn(Optional.of(currentVersion));

        // When
        Optional<DocumentVersion> result = documentVersioningService.getCurrentVersion(1L);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getVersionNumber()).isEqualTo(2);
        assertThat(result.get().isCurrentVersion()).isTrue();
    }

    @Test
    void testGetVersion() {
        // Given
        DocumentVersion version = DocumentVersion.builder()
            .id(1L)
            .versionNumber(1)
            .build();

        when(documentVersionRepository.findByDocumentIdAndVersionNumber(1L, 1))
            .thenReturn(Optional.of(version));

        // When
        Optional<DocumentVersion> result = documentVersioningService.getVersion(1L, 1);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getVersionNumber()).isEqualTo(1);
    }

    @Test
    void testGetVersionCount() {
        // Given
        when(documentVersionRepository.countByDocumentId(1L)).thenReturn(3L);

        // When
        long count = documentVersioningService.getVersionCount(1L);

        // Then
        assertThat(count).isEqualTo(3L);
    }

    @Test
    void testIsDuplicateHash() {
        // Given
        when(documentHashService.isDuplicate("testhash", 1L)).thenReturn(true);

        // When
        boolean isDuplicate = documentHashService.isDuplicate("testhash", 1L);

        // Then
        assertThat(isDuplicate).isTrue();
    }

    @Test
    void testCalculateSha256Hash() {
        // Given
        byte[] testData = "test data".getBytes();

        // When
        String hash = documentHashService.calculateSha256Hash(testData);

        // Then
        assertThat(hash).isNotNull();
        assertThat(hash.length()).isEqualTo(44); // Base64 encoded SHA-256
    }

    @Test
    void testVerifyFileIntegrity() {
        // Given
        byte[] testData = "test data".getBytes();
        String hash = documentHashService.calculateSha256Hash(testData);

        // When
        boolean isValid = documentHashService.verifyFileIntegrity(testData, hash);

        // Then
        assertThat(isValid).isTrue();
    }
}
