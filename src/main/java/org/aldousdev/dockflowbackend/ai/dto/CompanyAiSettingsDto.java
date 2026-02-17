package org.aldousdev.dockflowbackend.ai.dto;

import lombok.Data;

@Data
public class CompanyAiSettingsDto {
    private String provider;
    private String apiKey;
    private String model;
}
