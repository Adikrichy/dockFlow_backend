package org.aldousdev.dockflowbackend.workflow.parser;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class WorkflowXmlParser {

    @Data
    public static class WorkflowStep {
        private Integer order;
        private String roleName;
        private Integer roleLevel;
        private String action; // approve, review, sign, reject
        private boolean parallel;

        private String description;
        private List<String> allowedActions; // e.g. "DELEGATE,REQUEST_CHANGES"
    }

    @Data
    public static class RoutingRule {
        private Integer stepOrder;
        private String routingType; // onApprove, onReject, onTimeout
        private Integer targetStep; // null = complete workflow
        private String condition; // optional condition
        private String description;
    }

    @Data
    public static class WorkflowDefinition {
        private List<WorkflowStep> steps;
        private List<RoutingRule> routingRules;
    }

    /**
     * Parses the workflow XML steps.
     * Example XML:
     * <workflow>
     *   <step order="1" roleName="Manager" roleLevel="60" action="review" parallel="false"/>
     *   <step order="2" roleName="Director" roleLevel="80" action="approve" parallel="true"/>
     *   <step order="3" roleName="CEO" roleLevel="100" action="sign" parallel="false"/>
     * </workflow>
     */
    public static List<WorkflowStep> parseWorkflowSteps(String xmlContent) throws Exception {
        log.info("Parsing workflow XML");
        
        if (xmlContent == null || xmlContent.trim().isEmpty()) {
            throw new IllegalArgumentException("XML content cannot be empty");
        }

        List<WorkflowStep> steps = new ArrayList<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Disable XXE attacks
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xmlContent.getBytes()));

            NodeList stepNodes = doc.getElementsByTagName("step");
            log.info("Found {} steps in workflow", stepNodes.getLength());

            for (int i = 0; i < stepNodes.getLength(); i++) {
                Element stepElement = (Element) stepNodes.item(i);
                
                WorkflowStep step = new WorkflowStep();
                step.setOrder(Integer.parseInt(stepElement.getAttribute("order")));
                step.setRoleName(stepElement.getAttribute("roleName"));
                step.setRoleLevel(Integer.parseInt(stepElement.getAttribute("roleLevel")));
                step.setAction(stepElement.getAttribute("action"));
                step.setParallel(Boolean.parseBoolean(stepElement.getAttribute("parallel")));
                
                // Optional description
                if (stepElement.hasAttribute("description")) {
                    step.setDescription(stepElement.getAttribute("description"));
                }

                if (stepElement.hasAttribute("allowedActions")) {
                    String actionsStr = stepElement.getAttribute("allowedActions");
                    if (actionsStr != null && !actionsStr.isEmpty()) {
                        step.setAllowedActions(List.of(actionsStr.split(",")));
                    }
                }


                validateStep(step);
                steps.add(step);
            }

            // Sort by order
            steps.sort((a, b) -> Integer.compare(a.getOrder(), b.getOrder()));
            
            log.info("Successfully parsed {} workflow steps", steps.size());
            return steps;

        } catch (ParserConfigurationException | SAXException | IOException e) {
            log.error("Error parsing workflow XML", e);
            throw new IllegalArgumentException("Invalid XML format: " + e.getMessage(), e);
        }
    }

    private static void validateStep(WorkflowStep step) {
        if (step.getOrder() == null || step.getOrder() <= 0) {
            throw new IllegalArgumentException("Step order must be positive");
        }
        if (step.getRoleName() == null || step.getRoleName().isEmpty()) {
            throw new IllegalArgumentException("Step roleName is required");
        }
        if (step.getRoleLevel() == null || step.getRoleLevel() < 1 || step.getRoleLevel() > 100) {
            throw new IllegalArgumentException("Step roleLevel must be between 1 and 100");
        }
        if (step.getAction() == null || step.getAction().isEmpty()) {
            throw new IllegalArgumentException("Step action is required");
        }
    }

    /**
     * Parses the complete workflow definition including routing rules
     */
    public static WorkflowDefinition parseWorkflowDefinition(String xmlContent) throws Exception {
        log.info("Parsing complete workflow definition with routing rules");
        
        List<WorkflowStep> steps = parseWorkflowSteps(xmlContent);
        List<RoutingRule> routingRules = parseRoutingRules(xmlContent);
        
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setSteps(steps);
        definition.setRoutingRules(routingRules);
        
        return definition;
    }

    /**
     * Parses routing rules from XML
     * Example:
     * <onReject stepOrder="1" targetStep="1" description="Return to manager"/>
     * <onReject stepOrder="2" targetStep="1" description="Return to manager if director rejects"/>
     */
    private static List<RoutingRule> parseRoutingRules(String xmlContent) throws Exception {
        log.info("Parsing routing rules");
        
        List<RoutingRule> rules = new ArrayList<>();
        
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xmlContent.getBytes()));

            // Parse onReject rules
            NodeList rejectRules = doc.getElementsByTagName("onReject");
            for (int i = 0; i < rejectRules.getLength(); i++) {
                Element ruleElement = (Element) rejectRules.item(i);
                RoutingRule rule = parseRoutingElement(ruleElement, "onReject");
                rules.add(rule);
            }

            // Parse onApprove rules
            NodeList approveRules = doc.getElementsByTagName("onApprove");
            for (int i = 0; i < approveRules.getLength(); i++) {
                Element ruleElement = (Element) approveRules.item(i);
                RoutingRule rule = parseRoutingElement(ruleElement, "onApprove");
                rules.add(rule);
            }

            // Parse onTimeout rules
            NodeList timeoutRules = doc.getElementsByTagName("onTimeout");
            for (int i = 0; i < timeoutRules.getLength(); i++) {
                Element ruleElement = (Element) timeoutRules.item(i);
                RoutingRule rule = parseRoutingElement(ruleElement, "onTimeout");
                rules.add(rule);
            }

            log.info("Parsed {} routing rules", rules.size());
            return rules;

        } catch (ParserConfigurationException | SAXException | IOException e) {
            log.error("Error parsing routing rules", e);
            throw new IllegalArgumentException("Error parsing routing rules: " + e.getMessage(), e);
        }
    }

    private static RoutingRule parseRoutingElement(Element element, String routingType) {
        RoutingRule rule = new RoutingRule();
        rule.setStepOrder(Integer.parseInt(element.getAttribute("stepOrder")));
        rule.setRoutingType(routingType);
        
        // targetStep can be null (complete workflow)
        if (element.hasAttribute("targetStep")) {
            String targetStepStr = element.getAttribute("targetStep");
            if (!targetStepStr.isEmpty() && !targetStepStr.equals("null")) {
                rule.setTargetStep(Integer.parseInt(targetStepStr));
            }
        }
        
        if (element.hasAttribute("condition")) {
            rule.setCondition(element.getAttribute("condition"));
        }
        
        return rule;
    }

    /**
     * Generates example XML for workflow with conditional routing
     */
    public static String generateExampleXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <workflow>
                    <!-- Sequential steps -->
                    <step order="1" roleName="Manager" roleLevel="60" action="review" parallel="false" description="Initial review"/>
                    <step order="2" roleName="Director" roleLevel="80" action="approve" parallel="false" description="Director approval"/>
                    <step order="3" roleName="CEO" roleLevel="100" action="sign" parallel="false" description="Final signature"/>
                    <step order="4" roleName="Accountant" roleLevel="70" action="verify" parallel="false" description="Final verification"/>
                    <step order="5" roleName="Legal" roleLevel="75" action="legal_review" parallel="false" description="Legal review"/>

                    <!-- Conditional routing rules -->
                    <!-- Skip director for low-value documents -->
                    <onApprove stepOrder="1" condition="isLowValue" targetStep="3" description="Skip director for low-value documents"/>
                    <onApprove stepOrder="1" condition="!isLowValue" targetStep="2" description="Normal flow for high-value documents"/>

                    <!-- Return to manager if director rejects -->
                    <onReject stepOrder="2" targetStep="1" description="Return to manager if director rejects"/>

                    <!-- Different paths based on document type -->
                    <onApprove stepOrder="3" condition="isContract" targetStep="5" description="Legal review required for contracts"/>
                    <onApprove stepOrder="3" condition="!isContract" targetStep="4" description="Accountant verification for other documents"/>

                    <!-- Final rejection rules -->
                    <onReject stepOrder="4" targetStep="1" description="Return to manager if accountant rejects"/>
                    <onReject stepOrder="5" description="Complete workflow as rejected if legal rejects"/>
                </workflow>
                """;
    }
    /**
     * Generates example XML for parallel workflow
     */
    public static String generateParallelWorkflowXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <workflow>
                    <step order="1" roleName="Manager" roleLevel="60" action="review" parallel="false"/>
                    <step order="2" roleName="Lawyer" roleLevel="70" action="review" parallel="true"/>
                    <step order="2" roleName="Accountant" roleLevel="65" action="review" parallel="true"/>
                    <step order="3" roleName="CEO" roleLevel="100" action="sign" parallel="false"/>
                </workflow>
                """;
    }
}
