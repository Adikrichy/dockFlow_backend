package org.aldousdev.dockflowbackend.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "company_access_keys", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "company_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyAccessKey {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String publicKey;  // Base64-encoded public key
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String encryptedPrivateKey;  // PKCS#12 encrypted with user password
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    @Column
    private LocalDateTime lastUsedAt;
    
    @Column(nullable = false)
    private String keyAlgorithm = "RSA";
    
    @Column(nullable = false)
    private Integer keySize = 4096;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
