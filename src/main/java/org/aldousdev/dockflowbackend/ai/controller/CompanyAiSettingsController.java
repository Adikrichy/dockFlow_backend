package org.aldousdev.dockflowbackend.ai.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.ai.dto.CompanyAiSettingsDto;
import org.aldousdev.dockflowbackend.ai.entity.CompanyAiSettings;
import org.aldousdev.dockflowbackend.ai.service.CompanyAiSettingsService;
import org.aldousdev.dockflowbackend.auth.components.RequiresRoleLevel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/company/{companyId}/ai-settings")
@RequiredArgsConstructor
@Slf4j
public class CompanyAiSettingsController {

    private final CompanyAiSettingsService settingsService;

    @GetMapping
    @RequiresRoleLevel(60) // Manager+
    public ResponseEntity<Map<String, Object>> getSettings(@PathVariable Long companyId) {
        log.info("Getting AI settings for company {}", companyId);
        
        CompanyAiSettings settings = settingsService.getOrCreateSettings(companyId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("companyId", settings.getCompanyId());
        response.put("provider", settings.getProvider());
        response.put("model", settings.getModel());
        response.put("hasApiKey", settings.getApiKey() != null && !settings.getApiKey().isEmpty());
        
        return ResponseEntity.ok(response);
    }

    @PutMapping
    @RequiresRoleLevel(80) // Director+
    public ResponseEntity<Map<String, Object>> updateSettings(
            @PathVariable Long companyId,
            @RequestBody CompanyAiSettingsDto dto) {
        log.info("Updating AI settings for company {}: provider={}", companyId, dto.getProvider());
        
        CompanyAiSettings settings = settingsService.updateSettings(companyId, dto);
        
        Map<String, Object> response = new HashMap<>();
        response.put("companyId", settings.getCompanyId());
        response.put("provider", settings.getProvider());
        response.put("model", settings.getModel());
        response.put("hasApiKey", settings.getApiKey() != null && !settings.getApiKey().isEmpty());
        response.put("message", "AI settings updated successfully");
        
        return ResponseEntity.ok(response);
    }
}
