package org.aldousdev.dockflowbackend.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.auth.entity.Company;
import org.aldousdev.dockflowbackend.auth.entity.CompanyAccessKey;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.auth.repository.CompanyAccessKeyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class DigitalSignatureService {
    
    private final CompanyAccessKeyRepository accessKeyRepository;
    private static final int KEY_SIZE = 4096;
    private static final String KEY_ALGORITHM = "RSA";
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    
    /**
     * Generate RSA-4096 key pair for user-company access
     */
    @Transactional
    public CompanyAccessKey generateAccessKey(User user, Company company, String userPassword) {
        try {
            // Check if key already exists
            if (accessKeyRepository.existsByUserIdAndCompanyId(user.getId(), company.getId())) {
                throw new RuntimeException("Access key already exists for this user-company pair");
            }
            
            // Generate RSA-4096 key pair
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
            keyPairGenerator.initialize(KEY_SIZE, new SecureRandom());
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            
            // Encode public key to Base64
            String publicKeyBase64 = Base64.getEncoder().encodeToString(
                keyPair.getPublic().getEncoded()
            );
            
            // Create PKCS#12 keystore with private key encrypted by user password
            String encryptedPrivateKey = createPKCS12KeyStore(
                keyPair.getPrivate(), 
                keyPair.getPublic(),
                userPassword,
                user,
                company
            );
            
            // Save to database
            CompanyAccessKey accessKey = CompanyAccessKey.builder()
                .user(user)
                .company(company)
                .publicKey(publicKeyBase64)
                .encryptedPrivateKey(encryptedPrivateKey)
                .keyAlgorithm(KEY_ALGORITHM)
                .keySize(KEY_SIZE)
                .build();
            
            return accessKeyRepository.save(accessKey);
            
        } catch (Exception e) {
            log.error("Failed to generate access key", e);
            throw new RuntimeException("Failed to generate access key: " + e.getMessage());
        }
    }
    
    /**
     * Create PKCS#12 keystore (.p12 file) encrypted with user password
     */
    private String createPKCS12KeyStore(PrivateKey privateKey, PublicKey publicKey, 
                                        String password, User user, Company company) throws Exception {
        // Validate and convert password to ASCII (PKCS#12 requires ASCII-only passwords)
        String asciiPassword = convertToAscii(password);
        if (asciiPassword.isEmpty()) {
            throw new IllegalArgumentException("Password must contain at least one ASCII character");
        }
        
        // Create self-signed certificate for the key pair
        X509Certificate certificate = generateSelfSignedCertificate(privateKey, publicKey, user, company);
        
        // Create PKCS#12 keystore
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        
        // Store private key with certificate chain
        String alias = String.format("company_%d_user_%d", company.getId(), user.getId());
        keyStore.setKeyEntry(
            alias,
            privateKey,
            asciiPassword.toCharArray(),
            new Certificate[]{certificate}
        );
        
        // Convert keystore to byte array
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        keyStore.store(baos, asciiPassword.toCharArray());
        
        // Encode to Base64 for storage
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }
    
    /**
     * Convert password to ASCII-only string
     * Removes non-ASCII characters and keeps only printable ASCII (32-126)
     */
    private String convertToAscii(String password) {
        if (password == null) {
            return "";
        }
        
        StringBuilder asciiPassword = new StringBuilder();
        for (char c : password.toCharArray()) {
            // Keep only printable ASCII characters (32-126)
            if (c >= 32 && c <= 126) {
                asciiPassword.append(c);
            }
            // Skip non-ASCII characters
        }
        
        return asciiPassword.toString();
    }
    
    /**
     * Generate self-signed X.509 certificate
     */
    private X509Certificate generateSelfSignedCertificate(PrivateKey privateKey, PublicKey publicKey,
                                                          User user, Company company) throws Exception {
        // Certificate validity: 100 years (effectively permanent)
        Date startDate = new Date();
        Date endDate = Date.from(
            LocalDateTime.now().plusYears(100)
                .atZone(ZoneId.systemDefault())
                .toInstant()
        );
        
        // Certificate subject/issuer
        String subject = String.format(
            "CN=%s %s, O=%s, OU=Digital Signature, C=KZ",
            user.getFirstName(),
            user.getLastName(),
            company.getName()
        );
        
        X500Name issuer = new X500Name(subject);
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        
        // Build certificate
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
            issuer,
            serial,
            startDate,
            endDate,
            issuer,
            publicKey
        );
        
        // Sign certificate
        ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
            .build(privateKey);
        
        return new JcaX509CertificateConverter()
            .getCertificate(certBuilder.build(signer));
    }
    
    /**
     * Create .p12 file bytes for download
     */
    public byte[] createKeyFile(CompanyAccessKey accessKey, String userPassword) {
        try {
            // Convert password to ASCII
            String asciiPassword = convertToAscii(userPassword);
            
            // Decode PKCS#12 from Base64
            byte[] pkcs12Bytes = Base64.getDecoder().decode(accessKey.getEncryptedPrivateKey());
            
            // Verify password is correct by trying to load the keystore
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(new java.io.ByteArrayInputStream(pkcs12Bytes), asciiPassword.toCharArray());
            
            return pkcs12Bytes;
            
        } catch (Exception e) {
            log.error("Failed to create key file", e);
            throw new RuntimeException("Failed to create key file: " + e.getMessage());
        }
    }
    
    /**
     * Verify uploaded .p12 file and extract public key for validation
     * Password is not required - we use default password that was used during key creation
     */
    public boolean verifyKeyFile(byte[] p12FileBytes, Long userId, Long companyId) {
        try {
            // Use default password - same as used during key creation
            log.info("=== KEY VERIFICATION STARTED ===");
            log.info("userId: {}, companyId: {}", userId, companyId);
            log.info("Received file size: {} bytes", p12FileBytes.length);
            String defaultPassword = "defaultPassword123";
            
            // Load PKCS#12 keystore with default password
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(new java.io.ByteArrayInputStream(p12FileBytes), defaultPassword.toCharArray());
            log.info("Keystore successfully loaded with password");

            // Get the alias (should be company_{companyId}_user_{userId})
            String expectedAlias = String.format("company_%d_user_%d", companyId, userId);
            
            if (!keyStore.containsAlias(expectedAlias)) {
                log.warn("Key file does not contain expected alias: {}", expectedAlias);
                return false;
            }
            
            // Extract public key from certificate
            Certificate cert = keyStore.getCertificate(expectedAlias);
            PublicKey publicKey = cert.getPublicKey();
            String publicKeyBase64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
            
            // Verify against stored public key
            CompanyAccessKey storedKey = accessKeyRepository.findByUserIdAndCompanyId(userId, companyId)
                .orElseThrow(() -> new RuntimeException("No access key found for this user-company pair"));
            
            boolean isValid = storedKey.getPublicKey().equals(publicKeyBase64);
            
            if (isValid) {
                // Update last used timestamp
                storedKey.setLastUsedAt(LocalDateTime.now());
                accessKeyRepository.save(storedKey);
            }
            
            return isValid;
            
        } catch (Exception e) {
            log.error("Failed to verify key file", e);
            return false;
        }
    }
    
    /**
     * Regenerate access key (for lost key recovery by CEO/admin)
     */
    @Transactional
    public CompanyAccessKey regenerateAccessKey(User user, Company company, String newPassword) {
        // Delete old key if exists
        accessKeyRepository.findByUserIdAndCompanyId(user.getId(), company.getId())
            .ifPresent(accessKeyRepository::delete);
        
        // Generate new key
        return generateAccessKey(user, company, newPassword);
    }
}
