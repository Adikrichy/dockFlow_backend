package org.aldousdev.dockflowbackend.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.ai.dto.CompanyAiSettingsDto;
import org.aldousdev.dockflowbackend.ai.entity.CompanyAiSettings;
import org.aldousdev.dockflowbackend.ai.repository.CompanyAiSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyAiSettingsService {

    private final CompanyAiSettingsRepository repository;

    public Optional<CompanyAiSettings> getSettings(Long companyId) {
        return repository.findByCompanyId(companyId);
    }

    public CompanyAiSettings getOrCreateSettings(Long companyId) {
        return repository.findByCompanyId(companyId)
                .orElseGet(() -> {
                    CompanyAiSettings newSettings = new CompanyAiSettings();
                    newSettings.setCompanyId(companyId);
                    newSettings.setProvider("gemini"); // Default provider
                    return repository.save(newSettings);
                });
    }

    @Transactional
    public CompanyAiSettings updateSettings(Long companyId, CompanyAiSettingsDto dto) {
        CompanyAiSettings settings = getOrCreateSettings(companyId);
        
        if (dto.getProvider() != null) {
            settings.setProvider(dto.getProvider());
        }
        if (dto.getApiKey() != null) {
            settings.setApiKey(dto.getApiKey());
        }
        if (dto.getModel() != null) {
            settings.setModel(dto.getModel());
        }
        
        log.info("Updated AI settings for company {}: provider={}", companyId, settings.getProvider());
        return repository.save(settings);
    }

    public String getProviderForCompany(Long companyId) {
        return repository.findByCompanyId(companyId)
                .map(CompanyAiSettings::getProvider)
                .orElse("gemini");
    }

    public String getApiKeyForCompany(Long companyId) {
        return repository.findByCompanyId(companyId)
                .map(CompanyAiSettings::getApiKey)
                .orElse(null);
    }
}
