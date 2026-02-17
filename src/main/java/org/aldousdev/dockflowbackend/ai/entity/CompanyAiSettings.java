package org.aldousdev.dockflowbackend.ai.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "company_ai_settings", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"company_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyAiSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, unique = true)
    private Long companyId;

    @Column(name = "provider", nullable = false)
    private String provider = "gemini";

    @Column(name = "api_key")
    private String apiKey;

    @Column(name = "model")
    private String model;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
