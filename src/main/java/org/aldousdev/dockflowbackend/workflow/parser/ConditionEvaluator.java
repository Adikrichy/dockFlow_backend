package org.aldousdev.dockflowbackend.workflow.parser;

import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.workflow.entity.Document;
import org.aldousdev.dockflowbackend.workflow.enums.DocumentType;
import org.aldousdev.dockflowbackend.workflow.enums.Priority;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Evaluates conditions for workflow routing decisions
 */
@Slf4j
public class ConditionEvaluator {

    /**
     * Evaluates a condition against a document
     * Supports:
     * - Simple conditions: "isHighValue", "isLowValue", "isContract"
     * - Negation: "!isHighValue"
     * - Comparisons: "amount > 10000", "priority = HIGH"
     * - Document field access: "metadata.type = CONTRACT"
     *
     * @param condition The condition string to evaluate
     * @param document The document to evaluate against
     * @return true if condition is met, false otherwise
     */
    public static boolean evaluate(String condition, Document document) {
        if (condition == null || condition.trim().isEmpty()) {
            return true; // No condition means always true
        }

        condition = condition.trim();

        try {
            // Handle negation
            if (condition.startsWith("!")) {
                return !evaluate(condition.substring(1), document);
            }

            // Handle comparison conditions (amount > 10000)
            if (condition.contains(">") || condition.contains("<") || condition.contains("=")) {
                return evaluateComparison(condition, document);
            }

            // Handle predefined conditions
            return evaluatePredefinedCondition(condition, document);

        } catch (Exception e) {
            log.error("Error evaluating condition '{}': {}", condition, e.getMessage());
            return false; // Fail-safe: condition not met if evaluation fails
        }
    }

    /**
     * Evaluates comparison conditions like "amount > 10000" or "priority = HIGH"
     */
    private static boolean evaluateComparison(String condition, Document document) {
        // Split by comparison operators
        String[] operators = {">=", "<=", "!=", "=", ">", "<"};

        for (String op : operators) {
            if (condition.contains(op)) {
                String[] parts = condition.split(op, 2);
                if (parts.length == 2) {
                    String left = parts[0].trim();
                    String right = parts[1].trim();

                    return compareValues(left, right, op, document);
                }
            }
        }

        return false;
    }

    /**
     * Compares two values using the specified operator
     */
    private static boolean compareValues(String left, String right, String operator, Document document) {
        Object leftValue = resolveValue(left, document);
        Object rightValue = parseValue(right);

        if (leftValue == null || rightValue == null) {
            return false;
        }

        // Ensure both values are comparable
        if (leftValue.getClass() != rightValue.getClass()) {
            // Try to convert
            if (leftValue instanceof Number && rightValue instanceof Number) {
                // Convert to BigDecimal for safe comparison
                BigDecimal leftNum = toBigDecimal(leftValue);
                BigDecimal rightNum = toBigDecimal(rightValue);

                return compareNumbers(leftNum, rightNum, operator);
            }
            // String comparison for other types
            return compareStrings(leftValue.toString(), rightValue.toString(), operator);
        }

        // Same types - compare directly
        if (leftValue instanceof Number) {
            return compareNumbers(toBigDecimal(leftValue), toBigDecimal(rightValue), operator);
        } else {
            return compareStrings(leftValue.toString(), rightValue.toString(), operator);
        }
    }

    /**
     * Resolves a field value from the document
     */
    private static Object resolveValue(String field, Document document) {
        switch (field.toLowerCase()) {
            case "amount":
                return document.getAmount();
            case "priority":
                return document.getPriority();
            case "type":
                return document.getDocumentType();
            case "status":
                return document.getStatus();
            default:
                // Try to resolve from metadata if it's a complex field
                if (field.startsWith("metadata.")) {
                    return resolveMetadataField(field.substring(9), document);
                }
                return null;
        }
    }

    /**
     * Resolves metadata fields (for future extensibility)
     */
    private static Object resolveMetadataField(String field, Document document) {
        // Placeholder for metadata resolution
        // Could be JSON field in database
        return null;
    }

    /**
     * Parses a value from string (number, string, boolean)
     */
    private static Object parseValue(String value) {
        // Try to parse as number
        try {
            if (value.contains(".")) {
                return new BigDecimal(value);
            } else {
                return Long.parseLong(value);
            }
        } catch (NumberFormatException e) {
            // Not a number, try boolean
            if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                return Boolean.parseBoolean(value);
            }
            // Return as string
            return value;
        }
    }

    /**
     * Converts various number types to BigDecimal
     */
    private static BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        return BigDecimal.ZERO;
    }

    /**
     * Compares two BigDecimal numbers
     */
    private static boolean compareNumbers(BigDecimal left, BigDecimal right, String operator) {
        int comparison = left.compareTo(right);
        return switch (operator) {
            case ">" -> comparison > 0;
            case ">=" -> comparison >= 0;
            case "<" -> comparison < 0;
            case "<=" -> comparison <= 0;
            case "=" -> comparison == 0;
            case "!=" -> comparison != 0;
            default -> false;
        };
    }

    /**
     * Compares two strings
     */
    private static boolean compareStrings(String left, String right, String operator) {
        int comparison = left.compareToIgnoreCase(right);
        return switch (operator) {
            case "=" -> comparison == 0;
            case "!=" -> comparison != 0;
            case ">" -> comparison > 0;
            case ">=" -> comparison >= 0;
            case "<" -> comparison < 0;
            case "<=" -> comparison <= 0;
            default -> false;
        };
    }

    /**
     * Evaluates predefined conditions
     */
    private static boolean evaluatePredefinedCondition(String condition, Document document) {
        return switch (condition.toLowerCase()) {
            case "ishighvalue" -> document.getAmount() != null && document.getAmount().compareTo(BigDecimal.valueOf(50000)) > 0;
            case "islowvalue" -> document.getAmount() != null && document.getAmount().compareTo(BigDecimal.valueOf(5000)) <= 0;
            case "ismediumvalue" -> document.getAmount() != null &&
                                   document.getAmount().compareTo(BigDecimal.valueOf(5000)) > 0 &&
                                   document.getAmount().compareTo(BigDecimal.valueOf(50000)) <= 0;
            case "iscontract" -> DocumentType.CONTRACT.equals(document.getDocumentType());
            case "isinvoice" -> DocumentType.INVOICE.equals(document.getDocumentType());
            case "isurgent" -> Priority.HIGH.equals(document.getPriority()) || Priority.URGENT.equals(document.getPriority());
            case "isnormal" -> Priority.NORMAL.equals(document.getPriority()) || Priority.LOW.equals(document.getPriority());
            default -> false;
        };
    }

    /**
     * Creates a map of available conditions for documentation
     */
    public static Map<String, String> getAvailableConditions() {
        Map<String, String> conditions = new HashMap<>();
        conditions.put("isHighValue", "Document amount > 50,000");
        conditions.put("isLowValue", "Document amount <= 5,000");
        conditions.put("isMediumValue", "Document amount between 5,000 and 50,000");
        conditions.put("isContract", "Document type is CONTRACT");
        conditions.put("isInvoice", "Document type is INVOICE");
        conditions.put("isUrgent", "Document priority is HIGH or URGENT");
        conditions.put("isNormal", "Document priority is NORMAL or MEDIUM");
        conditions.put("amount > X", "Compare document amount with X");
        conditions.put("priority = VALUE", "Compare document priority with VALUE");
        return conditions;
    }
}
