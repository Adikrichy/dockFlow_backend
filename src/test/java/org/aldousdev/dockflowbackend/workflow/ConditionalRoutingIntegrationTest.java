package org.aldousdev.dockflowbackend.workflow;

import org.aldousdev.dockflowbackend.auth.entity.Company;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.workflow.entity.Document;
import org.aldousdev.dockflowbackend.workflow.entity.Task;
import org.aldousdev.dockflowbackend.workflow.entity.WorkflowInstance;
import org.aldousdev.dockflowbackend.workflow.enums.*;
import org.aldousdev.dockflowbackend.workflow.repository.TaskRepository;
import org.aldousdev.dockflowbackend.workflow.service.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ConditionalRoutingIntegrationTest {

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private TaskRepository taskRepository;

    private User testUser;
    private Company testCompany;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .email("test@example.com")
            .firstName("Test")
            .lastName("User")
            .build();

        testCompany = Company.builder()
            .name("Test Company")
            .description("Test company for conditional routing")
            .build();
    }

    @Test
    void testConditionalRoutingLowValueDocument() {
        // Given: Low-value document should skip Director approval
        Document lowValueDoc = createDocument(BigDecimal.valueOf(1000), DocumentType.INVOICE);

        String conditionalWorkflowXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <workflow>
                <step order="1" roleName="Manager" roleLevel="60" action="review" parallel="false"/>
                <step order="2" roleName="Director" roleLevel="80" action="approve" parallel="false"/>
                <step order="3" roleName="CEO" roleLevel="100" action="sign" parallel="false"/>
                <step order="4" roleName="Accountant" roleLevel="70" action="verify" parallel="false"/>

                <!-- Conditional routing: skip director for low-value documents -->
                <onApprove stepOrder="1" condition="isLowValue" targetStep="3" description="Skip director for low-value"/>
                <onApprove stepOrder="1" condition="!isLowValue" targetStep="2" description="Normal flow"/>
            </workflow>
            """;

        WorkflowInstance instance = WorkflowInstance.builder()
            .document(lowValueDoc)
            .initiatedBy(testUser)
            .status(WorkFlowStatus.IN_PROGRESS)
            .build();

        // When: Initialize workflow
        workflowService.initializeWorkflow(instance, conditionalWorkflowXml);

        // Then: Should have Manager task only initially
        List<Task> initialTasks = taskRepository.findByWorkflowInstance(instance);
        assertThat(initialTasks).hasSize(1);
        assertThat(initialTasks.get(0).getRequiredRoleName()).isEqualTo("Manager");

        // When: Manager approves
        Task managerTask = initialTasks.get(0);
        workflowService.approveTask(managerTask, testUser, "Approved - low value");

        // Then: Should skip to CEO (step 3), not Director (step 2)
        List<Task> afterApprovalTasks = taskRepository.findByWorkflowInstance(instance)
            .stream()
            .filter(t -> t.getStatus() == TaskStatus.PENDING)
            .toList();

        assertThat(afterApprovalTasks).hasSize(1);
        assertThat(afterApprovalTasks.get(0).getRequiredRoleName()).isEqualTo("CEO");
        assertThat(afterApprovalTasks.get(0).getStepOrder()).isEqualTo(3);
    }

    @Test
    void testConditionalRoutingHighValueDocument() {
        // Given: High-value document should go through normal flow
        Document highValueDoc = createDocument(BigDecimal.valueOf(100000), DocumentType.CONTRACT);

        String conditionalWorkflowXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <workflow>
                <step order="1" roleName="Manager" roleLevel="60" action="review" parallel="false"/>
                <step order="2" roleName="Director" roleLevel="80" action="approve" parallel="false"/>
                <step order="3" roleName="CEO" roleLevel="100" action="sign" parallel="false"/>

                <!-- Conditional routing: different paths based on value -->
                <onApprove stepOrder="1" condition="isLowValue" targetStep="3" description="Skip director"/>
                <onApprove stepOrder="1" condition="!isLowValue" targetStep="2" description="Normal flow"/>
            </workflow>
            """;

        WorkflowInstance instance = WorkflowInstance.builder()
            .document(highValueDoc)
            .initiatedBy(testUser)
            .status(WorkFlowStatus.IN_PROGRESS)
            .build();

        // When: Initialize and approve as Manager
        workflowService.initializeWorkflow(instance, conditionalWorkflowXml);

        Task managerTask = taskRepository.findByWorkflowInstance(instance)
            .stream()
            .filter(t -> t.getRequiredRoleName().equals("Manager"))
            .findFirst()
            .orElseThrow();

        workflowService.approveTask(managerTask, testUser, "Approved - high value");

        // Then: Should go to Director (step 2), not skip
        List<Task> pendingTasks = taskRepository.findByWorkflowInstance(instance)
            .stream()
            .filter(t -> t.getStatus() == TaskStatus.PENDING)
            .toList();

        assertThat(pendingTasks).hasSize(1);
        assertThat(pendingTasks.get(0).getRequiredRoleName()).isEqualTo("Director");
        assertThat(pendingTasks.get(0).getStepOrder()).isEqualTo(2);
    }

    @Test
    void testConditionalRoutingWithRejection() {
        // Given: Workflow with rejection routing
        Document contractDoc = createDocument(BigDecimal.valueOf(50000), DocumentType.CONTRACT);

        String workflowWithRejectionXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <workflow>
                <step order="1" roleName="Manager" roleLevel="60" action="review" parallel="false"/>
                <step order="2" roleName="Director" roleLevel="80" action="approve" parallel="false"/>
                <step order="3" roleName="CEO" roleLevel="100" action="sign" parallel="false"/>

                <!-- Rejection routing: return to manager -->
                <onReject stepOrder="2" targetStep="1" description="Return to manager if director rejects"/>
            </workflow>
            """;

        WorkflowInstance instance = WorkflowInstance.builder()
            .document(contractDoc)
            .initiatedBy(testUser)
            .status(WorkFlowStatus.IN_PROGRESS)
            .build();

        // When: Initialize workflow and go through approvals
        workflowService.initializeWorkflow(instance, workflowWithRejectionXml);

        // Approve Manager
        Task managerTask = findTaskByRole(instance, "Manager");
        workflowService.approveTask(managerTask, testUser, "Approved");

        // Reject Director
        Task directorTask = findTaskByRole(instance, "Director");
        workflowService.rejectTask(directorTask, testUser, "Needs revision");

        // Then: Should return to Manager (new task created)
        List<Task> pendingTasks = taskRepository.findByWorkflowInstance(instance)
            .stream()
            .filter(t -> t.getStatus() == TaskStatus.PENDING)
            .toList();

        assertThat(pendingTasks).hasSize(1);
        assertThat(pendingTasks.get(0).getRequiredRoleName()).isEqualTo("Manager");
        assertThat(pendingTasks.get(0).getStepOrder()).isEqualTo(1);

        // Director task should be cancelled
        List<Task> directorTasks = taskRepository.findByWorkflowInstance(instance)
            .stream()
            .filter(t -> t.getRequiredRoleName().equals("Director"))
            .toList();

        assertThat(directorTasks).anyMatch(t -> t.getStatus() == TaskStatus.REJECTED);
    }

    @Test
    void testConditionalRoutingDocumentTypeBased() {
        // Given: Different paths for contracts vs invoices
        Document contractDoc = createDocument(BigDecimal.valueOf(50000), DocumentType.CONTRACT);

        String typeBasedWorkflowXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <workflow>
                <step order="1" roleName="Manager" roleLevel="60" action="review" parallel="false"/>
                <step order="2" roleName="CEO" roleLevel="100" action="sign" parallel="false"/>
                <step order="3" roleName="Legal" roleLevel="75" action="legal_review" parallel="false"/>
                <step order="4" roleName="Accountant" roleLevel="70" action="verify" parallel="false"/>

                <!-- Type-based routing -->
                <onApprove stepOrder="1" condition="isContract" targetStep="3" description="Legal review for contracts"/>
                <onApprove stepOrder="1" condition="!isContract" targetStep="4" description="Accounting for invoices"/>
            </workflow>
            """;

        WorkflowInstance instance = WorkflowInstance.builder()
            .document(contractDoc)
            .initiatedBy(testUser)
            .status(WorkFlowStatus.IN_PROGRESS)
            .build();

        // When: Approve manager
        workflowService.initializeWorkflow(instance, typeBasedWorkflowXml);

        Task managerTask = findTaskByRole(instance, "Manager");
        workflowService.approveTask(managerTask, testUser, "Approved");

        // Then: Should go to Legal for contract
        List<Task> pendingTasks = taskRepository.findByWorkflowInstance(instance)
            .stream()
            .filter(t -> t.getStatus() == TaskStatus.PENDING)
            .toList();

        assertThat(pendingTasks).hasSize(1);
        assertThat(pendingTasks.get(0).getRequiredRoleName()).isEqualTo("Legal");
        assertThat(pendingTasks.get(0).getStepOrder()).isEqualTo(3);
    }

    private Document createDocument(BigDecimal amount, DocumentType documentType) {
        return Document.builder()
            .amount(amount)
            .originalFilename("test.pdf")
            .filePath("./test.pdf")
            .company(testCompany)
            .uploadedBy(testUser)
            .build();
    }

    private Task findTaskByRole(WorkflowInstance instance, String roleName) {
        return taskRepository.findByWorkflowInstance(instance)
            .stream()
            .filter(t -> t.getRequiredRoleName().equals(roleName) && t.getStatus() == TaskStatus.PENDING)
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Task not found for role: " + roleName));
    }
}
