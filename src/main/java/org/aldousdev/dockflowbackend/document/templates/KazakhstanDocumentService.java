package org.aldousdev.dockflowbackend.document.templates;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * Сервис для работы с казахстанскими документами
 * Service for Kazakhstan document management
 */
@Service
@Transactional
public class KazakhstanDocumentService {
    
    private static final Logger log = Logger.getLogger(KazakhstanDocumentService.class.getName());
    
    private final KazakhstanDocumentTemplate.KazakhstanTemplates templates;
    
    public KazakhstanDocumentService() {
        this.templates = new KazakhstanDocumentTemplate.KazakhstanTemplates();
    }
    
    /**
     * Получить все казахстанские шаблоны
     */
    public List<KazakhstanDocumentTemplate> getAllKazakhstanTemplates() {
        log.info("Getting all Kazakhstan document templates");
        return KazakhstanDocumentTemplate.KazakhstanTemplates.getAllTemplates();
    }
    
    /**
     * Получить шаблон по типу документа
     */
    public Optional<KazakhstanDocumentTemplate> getTemplateByType(String documentType) {
        log.info("Getting Kazakhstan template by type: " + documentType);
        KazakhstanDocumentTemplate template = KazakhstanDocumentTemplate.KazakhstanTemplates.getTemplateByType(documentType);
        return Optional.ofNullable(template);
    }
    
    /**
     * Валидация казахстанских полей
     */
    public boolean validateKazakhstanField(String fieldName, String value, String documentType) {
        log.info("Validating Kazakhstan field: " + fieldName + " = " + value + " for document type: " + documentType);
        
        Optional<KazakhstanDocumentTemplate> templateOpt = getTemplateByType(documentType);
        if (templateOpt.isEmpty()) {
            log.warning("Template not found for document type: " + documentType);
            return false;
        }
        
        KazakhstanDocumentTemplate template = templateOpt.get();
        return template.getRequiredFields().stream()
            .filter(field -> field.getFieldName().equals(fieldName))
            .findFirst()
            .map(field -> validateField(field, value))
            .orElse(false);
    }
    
    /**
     * Валидация одного поля
     */
    private boolean validateField(KazakhstanDocumentTemplate.TemplateField field, String value) {
        if (field.isRequired() && (value == null || value.trim().isEmpty())) {
            log.warning("Required field " + field.getFieldName() + " is empty");
            return false;
        }
        
        if (field.getValidationRules() != null && value != null) {
            boolean isValid = value.matches(field.getValidationRules());
            if (!isValid) {
                log.warning("Field " + field.getFieldName() + " with value " + value + 
                    " doesn't match validation pattern: " + field.getValidationRules());
            }
            return isValid;
        }
        
        return true;
    }
    
    /**
     * Валидация БИН (Бизнес идентификационный номер)
     */
    public boolean isValidBin(String bin) {
        if (bin == null || bin.length() != 12) {
            return false;
        }
        
        try {
            // Проверка на цифры
            Long.parseLong(bin);
            
            // Простая валидация для казахстанских БИН
            // БИН физического лица начинается с цифры, обозначающей век рождения
            // БИН юридического лица начинается с других цифр
            char firstDigit = bin.charAt(0);
            
            // БИН юридического лица (1-4, 5-6)
            if (firstDigit >= '1' && firstDigit <= '6') {
                return true;
            }
            
            // БИН индивидуального предпринимателя (7-9)
            if (firstDigit >= '7' && firstDigit <= '9') {
                return true;
            }
            
            return false;
            
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Валидация ИИН (Индивидуальный идентификационный номер)
     */
    public boolean isValidIin(String iin) {
        // БИН и ИИН имеют одинаковую структуру в Казахстане
        return isValidBin(iin);
    }
    
    /**
     * Форматирование суммы в тенге
     */
    public String formatTengeAmount(Double amount) {
        if (amount == null) {
            return "0,00 ₸";
        }
        
        return String.format("%,.2f ₸", amount)
            .replace(',', ' ')
            .replace('.', ',');
    }
    
    /**
     * Получение казахстанских типов документов с переводами
     */
    public List<DocumentTypeDTO> getKazakhstanDocumentTypes() {
        return Arrays.stream(KazakhstanDocumentTemplate.KazakhstanDocumentType.values())
            .map(type -> new DocumentTypeDTO(
                type.name(),
                type.getRuName(),
                type.getKzName(),
                type.getRuName() // Можно добавить английский перевод
            ))
            .toList();
    }
    
    /**
     * DTO для типа документа
     */
    public record DocumentTypeDTO(
        String code,
        String nameRu,
        String nameKz,
        String nameEn
    ) {}
}