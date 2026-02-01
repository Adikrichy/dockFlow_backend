package org.aldousdev.dockflowbackend.document.templates;

import java.util.List;
import java.util.ArrayList;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Казахстанские форматы документов
 * Kazakhstan Document Templates
 */
public class KazakhstanDocumentTemplate {
    
    private String documentType;
    private String templateNameKz;
    private String templateNameRu;
    private String templateNameEn;
    private String descriptionKz;
    private String descriptionRu;
    private String descriptionEn;
    private List<TemplateField> requiredFields;
    private String xmlTemplate;
    
    // Constructors
    public KazakhstanDocumentTemplate() {}
    
    public KazakhstanDocumentTemplate(String documentType, String templateNameKz, String templateNameRu, String templateNameEn,
                                 String descriptionKz, String descriptionRu, String descriptionEn,
                                 List<TemplateField> requiredFields, String xmlTemplate) {
        this.documentType = documentType;
        this.templateNameKz = templateNameKz;
        this.templateNameRu = templateNameRu;
        this.templateNameEn = templateNameEn;
        this.descriptionKz = descriptionKz;
        this.descriptionRu = descriptionRu;
        this.descriptionEn = descriptionEn;
        this.requiredFields = requiredFields;
        this.xmlTemplate = xmlTemplate;
    }
    
    // Getters and Setters
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    
    public String getTemplateNameKz() { return templateNameKz; }
    public void setTemplateNameKz(String templateNameKz) { this.templateNameKz = templateNameKz; }
    
    public String getTemplateNameRu() { return templateNameRu; }
    public void setTemplateNameRu(String templateNameRu) { this.templateNameRu = templateNameRu; }
    
    public String getTemplateNameEn() { return templateNameEn; }
    public void setTemplateNameEn(String templateNameEn) { this.templateNameEn = templateNameEn; }
    
    public String getDescriptionKz() { return descriptionKz; }
    public void setDescriptionKz(String descriptionKz) { this.descriptionKz = descriptionKz; }
    
    public String getDescriptionRu() { return descriptionRu; }
    public void setDescriptionRu(String descriptionRu) { this.descriptionRu = descriptionRu; }
    
    public String getDescriptionEn() { return descriptionEn; }
    public void setDescriptionEn(String descriptionEn) { this.descriptionEn = descriptionEn; }
    
    public List<TemplateField> getRequiredFields() { return requiredFields; }
    public void setRequiredFields(List<TemplateField> requiredFields) { this.requiredFields = requiredFields; }
    
    public String getXmlTemplate() { return xmlTemplate; }
    public void setXmlTemplate(String xmlTemplate) { this.xmlTemplate = xmlTemplate; }
    
    /**
     * Типы казахстанских документов
     */
    public enum KazakhstanDocumentType {
        KS2_ACT("КС-2 Акт выполненных работ", "КС-2 Орындалған жұмыстар акты"),
        INVOICE("Счет-фактура", "Есеп-фактура"),
        LABOR_CONTRACT("Трудовой договор", "Еңбек шарты"),
        PURCHASE_ORDER("Заказ на поставку", "Жеткізуге тапсырыс"),
        SERVICE_AGREEMENT("Договор оказания услуг", "Қызмет көрсету шарты"),
        RENT_AGREEMENT("Договор аренды", "Аренда шарты"),
        NDA("Соглашение о неразглашении", "Сыйластық туралы келісім"),
        TENDER_DOCUMENT("Тендерная документация", "Тендерлік құжаттама"),
        CONSTRUCTION_ACT("Акт освидетельствования скрытых работ", "Жасырын жұмыстарды тексеру акты"),
        MATERIALS_ACCEPTANCE_ACT("Акт приемки материалов", "Материалдарды қабылдау акты");
        
        private final String ruName;
        private final String kzName;
        
        KazakhstanDocumentType(String ruName, String kzName) {
            this.ruName = ruName;
            this.kzName = kzName;
        }
        
        public String getRuName() { return ruName; }
        public String getKzName() { return kzName; }
    }
    
    public static class TemplateField {
        private String fieldName;
        private String fieldLabelRu;
        private String fieldLabelKz;
        private String fieldType; // TEXT, NUMBER, DATE, SELECT
        private boolean required;
        private String validationRules;
        private Object defaultValue;
        
        public TemplateField() {}
        
        public TemplateField(String fieldName, String fieldLabelRu, String fieldLabelKz, String fieldType, 
                         boolean required, String validationRules, Object defaultValue) {
            this.fieldName = fieldName;
            this.fieldLabelRu = fieldLabelRu;
            this.fieldLabelKz = fieldLabelKz;
            this.fieldType = fieldType;
            this.required = required;
            this.validationRules = validationRules;
            this.defaultValue = defaultValue;
        }
        
        // Getters and Setters
        public String getFieldName() { return fieldName; }
        public void setFieldName(String fieldName) { this.fieldName = fieldName; }
        
        public String getFieldLabelRu() { return fieldLabelRu; }
        public void setFieldLabelRu(String fieldLabelRu) { this.fieldLabelRu = fieldLabelRu; }
        
        public String getFieldLabelKz() { return fieldLabelKz; }
        public void setFieldLabelKz(String fieldLabelKz) { this.fieldLabelKz = fieldLabelKz; }
        
        public String getFieldType() { return fieldType; }
        public void setFieldType(String fieldType) { this.fieldType = fieldType; }
        
        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }
        
        public String getValidationRules() { return validationRules; }
        public void setValidationRules(String validationRules) { this.validationRules = validationRules; }
        
        public Object getDefaultValue() { return defaultValue; }
        public void setDefaultValue(Object defaultValue) { this.defaultValue = defaultValue; }
    }
    
    /**
     * Предустановленные шаблоны для казахстанских документов
     */
    public static class KazakhstanTemplates {
        
        /**
         * Шаблон КС-2 акта выполненных работ
         */
        public static KazakhstanDocumentTemplate getKS2ActTemplate() {
            List<TemplateField> fields = new ArrayList<>();
            fields.add(new TemplateField("contractNumber", "Номер договора", "Шарт нөмірі", "TEXT", true, "^[А-Яа-я0-9\\-/]+$", null));
            fields.add(new TemplateField("contractDate", "Дата договора", "Шарт күні", "DATE", true, null, null));
            fields.add(new TemplateField("customerBin", "БИН заказчика", "Тапсырушының БИН", "TEXT", true, "^\\d{12}$", null));
            fields.add(new TemplateField("contractorBin", "БИН подрядчика", "Подрядчының БИН", "TEXT", true, "^\\d{12}$", null));
            fields.add(new TemplateField("workStartDate", "Дата начала работ", "Жұмыс басталу күні", "DATE", true, null, null));
            fields.add(new TemplateField("workEndDate", "Дата окончания работ", "Жұмыс аяқталу күні", "DATE", true, null, null));
            fields.add(new TemplateField("totalAmount", "Сумма, тенге", "Сомасы, теңге", "NUMBER", true, "^\\d+(\\.\\d{1,2})?$", null));
            fields.add(new TemplateField("vatAmount", "НДС, тенге", "ҚҚС, теңге", "NUMBER", false, "^\\d+(\\.\\d{1,2})?$", null));
            fields.add(new TemplateField("workDescription", "Описание работ", "Жұмыстар сипаттамасы", "TEXTAREA", true, null, null));
            
            String xmlTemplate = """
                <workflow>
                  <step order="1" roleName="Manager" roleLevel="60" action="review" parallel="false">
                    <kzFields>
                      <field name="contractNumber" required="true" validation="^[А-Яа-я0-9\\-/]+$"/>
                      <field name="customerBin" required="true" validation="^\\d{12}$"/>
                      <field name="contractorBin" required="true" validation="^\\d{12}$"/>
                    </kzFields>
                  </step>
                  <step order="2" roleName="Accounting" roleLevel="65" action="verify" parallel="false">
                    <kzFields>
                      <field name="totalAmount" required="true" currency="KZT"/>
                      <field name="vatAmount" validation="^[0-9]*\\.?[0-9]{1,2}$"/>
                    </kzFields>
                  </step>
                  <step order="3" roleName="Director" roleLevel="80" action="approve" parallel="false">
                    <kzFields>
                      <field name="workDescription" required="true" minLength="10"/>
                    </kzFields>
                  </step>
                </workflow>
                """;
            
            return new KazakhstanDocumentTemplate(
                "KS2_ACT",
                "КС-2 Орындалған жұмыстар акты",
                "КС-2 Акт выполненных работ", 
                "KS-2 Certificate of Completed Works",
                "КС-2 нысаны бойынша орындалған жұмыстарды қабылдау акты",
                "Акт приемки выполненных работ по форме КС-2",
                "Certificate of acceptance of completed works in KS-2 form",
                fields,
                xmlTemplate
            );
        }
        
        /**
         * Шаблон счета-фактуры
         */
        public static KazakhstanDocumentTemplate getInvoiceTemplate() {
            List<TemplateField> fields = new ArrayList<>();
            fields.add(new TemplateField("invoiceNumber", "Номер счета", "Есеп нөмірі", "TEXT", true, "^[0-9]+$", null));
            fields.add(new TemplateField("invoiceDate", "Дата счета", "Есеп күні", "DATE", true, null, null));
            fields.add(new TemplateField("customerName", "Наименование покупателя", "Сатып алушының атауы", "TEXT", true, null, null));
            fields.add(new TemplateField("customerBin", "БИН покупателя", "Сатып алушының БИН", "TEXT", true, "^\\d{12}$", null));
            fields.add(new TemplateField("supplierName", "Наименование поставщика", "Жеткізушінің атауы", "TEXT", true, null, null));
            fields.add(new TemplateField("supplierBin", "БИН поставщика", "Жеткізушінің БИН", "TEXT", true, "^\\d{12}$", null));
            fields.add(new TemplateField("paymentDueDate", "Срок оплаты", "Төлем мерзімі", "DATE", true, null, null));
            fields.add(new TemplateField("subtotal", "Итого без НДС", "ҚҚСсыз жалпы сомасы", "NUMBER", true, "^\\d+(\\.\\d{2})?$", null));
            fields.add(new TemplateField("vatRate", "Ставка НДС, %", "ҚҚС мөлшері, %", "SELECT", true, "^(0|12)$", "12"));
            fields.add(new TemplateField("vatAmount", "Сумма НДС", "ҚҚС сомасы", "NUMBER", true, "^\\d+(\\.\\d{2})?$", null));
            fields.add(new TemplateField("totalAmount", "Итого с НДС", "ҚҚСімен жалпы сомасы", "NUMBER", true, "^\\d+(\\.\\d{2})?$", null));
            fields.add(new TemplateField("items", "Товары/Услуги", "Тауарлар/Қызметтер", "TABLE", true, null, null));
            
            String xmlTemplate = """
                <workflow>
                  <step order="1" roleName="Sales" roleLevel="50" action="create" parallel="false">
                    <kzFields>
                      <field name="customerBin" required="true" validation="^\\d{12}$"/>
                      <field name="supplierBin" required="true" validation="^\\d{12}$"/>
                      <field name="vatRate" required="true" options="0,12"/>
                    </kzFields>
                  </step>
                  <step order="2" roleName="Accounting" roleLevel="65" action="verify" parallel="false">
                    <kzFields>
                      <field name="totalAmount" required="true" currency="KZT"/>
                      <field name="vatAmount" formula="subtotal * vatRate / 100"/>
                    </kzFields>
                  </step>
                  <step order="3" roleName="Manager" roleLevel="60" action="approve" parallel="false"/>
                </workflow>
                """;
            
            return new KazakhstanDocumentTemplate(
                "INVOICE",
                "Есеп-фактура",
                "Счет-фактура",
                "Invoice",
                "Қазақстан компаниялары үшін есеп-фактура",
                "Счет-фактура для казахстанских компаний",
                "Invoice for Kazakhstan companies",
                fields,
                xmlTemplate
            );
        }
        
        /**
         * Получить все казахстанские шаблоны
         */
        public static List<KazakhstanDocumentTemplate> getAllTemplates() {
            List<KazakhstanDocumentTemplate> templates = new ArrayList<>();
            templates.add(getKS2ActTemplate());
            templates.add(getInvoiceTemplate());
            return templates;
        }
        
        /**
         * Получить шаблон по типу
         */
        public static KazakhstanDocumentTemplate getTemplateByType(String documentType) {
            return getAllTemplates().stream()
                .filter(template -> template.getDocumentType().equals(documentType))
                .findFirst()
                .orElse(null);
        }
    }
}