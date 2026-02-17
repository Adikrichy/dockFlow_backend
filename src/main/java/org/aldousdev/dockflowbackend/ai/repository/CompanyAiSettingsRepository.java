package org.aldousdev.dockflowbackend.ai.repository;

import org.aldousdev.dockflowbackend.ai.entity.CompanyAiSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompanyAiSettingsRepository extends JpaRepository<CompanyAiSettings, Long> {
    Optional<CompanyAiSettings> findByCompanyId(Long companyId);
}
