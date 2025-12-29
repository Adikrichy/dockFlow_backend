package org.aldousdev.dockflowbackend.workflow;

import org.aldousdev.dockflowbackend.auth.entity.Company;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.workflow.entity.Document;
import org.aldousdev.dockflowbackend.workflow.enums.DocumentStatus;
import org.aldousdev.dockflowbackend.workflow.enums.DocumentType;
import org.aldousdev.dockflowbackend.workflow.enums.Priority;
import org.aldousdev.dockflowbackend.workflow.parser.ConditionEvaluator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ConditionEvaluatorTest {

    @Test
    void testPredefinedConditions() {
        // Test isHighValue
        Document highValueDoc = createDocument(BigDecimal.valueOf(100000), Priority.NORMAL, DocumentType.CONTRACT);
        assertThat(ConditionEvaluator.evaluate("isHighValue", highValueDoc)).isTrue();

        Document lowValueDoc = createDocument(BigDecimal.valueOf(1000), Priority.NORMAL, DocumentType.CONTRACT);
        assertThat(ConditionEvaluator.evaluate("isHighValue", lowValueDoc)).isFalse();

        // Test isLowValue
        assertThat(ConditionEvaluator.evaluate("isLowValue", lowValueDoc)).isTrue();
        assertThat(ConditionEvaluator.evaluate("isLowValue", highValueDoc)).isFalse();

        // Test isContract
        Document contractDoc = createDocument(BigDecimal.valueOf(10000), Priority.NORMAL, DocumentType.CONTRACT);
        assertThat(ConditionEvaluator.evaluate("isContract", contractDoc)).isTrue();

        Document invoiceDoc = createDocument(BigDecimal.valueOf(10000), Priority.NORMAL, DocumentType.INVOICE);
        assertThat(ConditionEvaluator.evaluate("isContract", invoiceDoc)).isFalse();

        // Test isUrgent
        Document urgentDoc = createDocument(BigDecimal.valueOf(10000), Priority.URGENT, DocumentType.CONTRACT);
        assertThat(ConditionEvaluator.evaluate("isUrgent", urgentDoc)).isTrue();

        Document normalDoc = createDocument(BigDecimal.valueOf(10000), Priority.NORMAL, DocumentType.CONTRACT);
        assertThat(ConditionEvaluator.evaluate("isUrgent", normalDoc)).isFalse();
    }

    @Test
    void testNegationConditions() {
        Document lowValueDoc = createDocument(BigDecimal.valueOf(1000), Priority.NORMAL, DocumentType.CONTRACT);

        // !isHighValue should be true for low value documents
        assertThat(ConditionEvaluator.evaluate("!isHighValue", lowValueDoc)).isTrue();

        // !isLowValue should be false for low value documents
        assertThat(ConditionEvaluator.evaluate("!isLowValue", lowValueDoc)).isFalse();

        Document contractDoc = createDocument(BigDecimal.valueOf(10000), Priority.NORMAL, DocumentType.CONTRACT);

        // !isContract should be false for contract documents
        assertThat(ConditionEvaluator.evaluate("!isContract", contractDoc)).isFalse();
    }

    @Test
    void testComparisonConditions() {
        Document doc = createDocument(BigDecimal.valueOf(25000), Priority.NORMAL, DocumentType.CONTRACT);

        // Amount comparisons
        assertThat(ConditionEvaluator.evaluate("amount > 20000", doc)).isTrue();
        assertThat(ConditionEvaluator.evaluate("amount > 30000", doc)).isFalse();
        assertThat(ConditionEvaluator.evaluate("amount <= 25000", doc)).isTrue();
        assertThat(ConditionEvaluator.evaluate("amount = 25000", doc)).isTrue();
        assertThat(ConditionEvaluator.evaluate("amount != 30000", doc)).isTrue();

        // Priority comparisons
        assertThat(ConditionEvaluator.evaluate("priority = NORMAL", doc)).isTrue();
        assertThat(ConditionEvaluator.evaluate("priority != HIGH", doc)).isTrue();

        // Document type comparisons
        assertThat(ConditionEvaluator.evaluate("type = CONTRACT", doc)).isTrue();
        assertThat(ConditionEvaluator.evaluate("type != INVOICE", doc)).isTrue();
    }

    @Test
    void testComplexConditions() {
        // High value contract with high priority
        Document highValueUrgentContract = createDocument(BigDecimal.valueOf(100000), Priority.URGENT, DocumentType.CONTRACT);

        // Should match both conditions
        assertThat(ConditionEvaluator.evaluate("isHighValue", highValueUrgentContract)).isTrue();
        assertThat(ConditionEvaluator.evaluate("isUrgent", highValueUrgentContract)).isTrue();
        assertThat(ConditionEvaluator.evaluate("isContract", highValueUrgentContract)).isTrue();

        // Low value normal invoice
        Document lowValueNormalInvoice = createDocument(BigDecimal.valueOf(1000), Priority.NORMAL, DocumentType.INVOICE);

        assertThat(ConditionEvaluator.evaluate("isLowValue", lowValueNormalInvoice)).isTrue();
        assertThat(ConditionEvaluator.evaluate("!isUrgent", lowValueNormalInvoice)).isTrue();
        assertThat(ConditionEvaluator.evaluate("!isContract", lowValueNormalInvoice)).isTrue();
    }

    @Test
    void testEdgeCases() {
        // Null or empty conditions
        Document doc = createDocument(BigDecimal.valueOf(10000), Priority.NORMAL, DocumentType.CONTRACT);

        assertThat(ConditionEvaluator.evaluate(null, doc)).isTrue();
        assertThat(ConditionEvaluator.evaluate("", doc)).isTrue();
        assertThat(ConditionEvaluator.evaluate("   ", doc)).isTrue();

        // Invalid conditions should return false
        assertThat(ConditionEvaluator.evaluate("invalidCondition", doc)).isFalse();
        assertThat(ConditionEvaluator.evaluate("amount > invalid", doc)).isFalse();

        // Document with null amount
        Document docWithNullAmount = createDocument(null, Priority.NORMAL, DocumentType.CONTRACT);
        assertThat(ConditionEvaluator.evaluate("amount > 1000", docWithNullAmount)).isFalse();
    }

    @Test
    void testAvailableConditions() {
        var conditions = ConditionEvaluator.getAvailableConditions();

        assertThat(conditions).isNotEmpty();
        assertThat(conditions).containsKey("isHighValue");
        assertThat(conditions).containsKey("isLowValue");
        assertThat(conditions).containsKey("isContract");
        assertThat(conditions).containsKey("isUrgent");
    }

    private Document createDocument(BigDecimal amount, Priority priority, DocumentType documentType) {
        return Document.builder()
            .amount(amount)
            .priority(priority)
            .documentType(documentType)
            .originalFilename("test.pdf")
            .filePath("./test.pdf")
            .company(Company.builder().name("Test Company").build())
            .uploadedBy(User.builder().email("test@example.com").firstName("Test").lastName("User").build())
            .build();
    }
}
