package org.aldousdev.dockflowbackend.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.aldousdev.dockflowbackend.auth.entity.Company;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.workflow.dto.request.BulkTaskRequest;
import org.aldousdev.dockflowbackend.workflow.dto.response.BulkOperationResponse;
import org.aldousdev.dockflowbackend.workflow.entity.Document;
import org.aldousdev.dockflowbackend.workflow.entity.Task;
import org.aldousdev.dockflowbackend.workflow.entity.WorkflowInstance;
import org.aldousdev.dockflowbackend.workflow.enums.TaskStatus;
import org.aldousdev.dockflowbackend.workflow.enums.WorkFlowStatus;
import org.aldousdev.dockflowbackend.workflow.repository.TaskRepository;
import org.aldousdev.dockflowbackend.workflow.service.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BulkWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private TaskRepository taskRepository;

    private User testUser;
    private Company testCompany;
    private Document testDocument;
    private WorkflowInstance testWorkflow;

    @BeforeEach
    void setUp() {
        // Создаем тестовые данные аналогично другим тестам
        testUser = User.builder()
            .email("test@example.com")
            .firstName("Test")
            .lastName("User")
            .build();

        testCompany = Company.builder()
            .name("Test Company")
            .description("Test company for bulk operations")
            .build();

        testDocument = Document.builder()
            .filename("test.pdf")
            .company(testCompany)
            .build();

        testWorkflow = WorkflowInstance.builder()
            .document(testDocument)
            .initiatedBy(testUser)
            .status(WorkFlowStatus.IN_PROGRESS)
            .build();
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void testBulkApproveTasks() throws Exception {
        // Given: Workflow с параллельными задачами
        String parallelWorkflowXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <workflow>
                <step order="1" roleName="Manager" roleLevel="60" action="review" parallel="false"/>
                <step order="2" roleName="Lawyer" roleLevel="70" action="review" parallel="true"/>
                <step order="2" roleName="Accountant" roleLevel="65" action="review" parallel="true"/>
            </workflow>
            """;

        workflowService.initializeWorkflow(testWorkflow, parallelWorkflowXml);

        // Получаем все pending задачи
        List<Task> pendingTasks = taskRepository.findByWorkflowInstance(testWorkflow).stream()
            .filter(t -> t.getStatus() == TaskStatus.PENDING)
            .toList();

        assertThat(pendingTasks).hasSize(3); // 1 Manager + 2 parallel

        // When: Выполняем bulk approve через API
        BulkTaskRequest request = new BulkTaskRequest();
        request.setTaskIds(pendingTasks.stream().map(Task::getId).toList());
        request.setComment("Bulk approved for testing");

        String responseJson = mockMvc.perform(post("/api/workflow/tasks/bulk-approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();

        BulkOperationResponse response = objectMapper.readValue(responseJson, BulkOperationResponse.class);

        // Then: Проверяем результат
        assertThat(response.getTotalTasks()).isEqualTo(3);
        assertThat(response.getSuccessfulCount()).isEqualTo(3); // Все задачи одобрены
        assertThat(response.getErrors()).isEmpty();

        // Проверяем, что задачи действительно одобрены
        List<Task> updatedTasks = taskRepository.findAllById(request.getTaskIds());
        assertThat(updatedTasks).allMatch(task -> task.getStatus() == TaskStatus.APPROVED);
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void testBulkApproveWithPermissions() throws Exception {
        // Given: Workflow с задачами разных уровней
        String workflowXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <workflow>
                <step order="1" roleName="Manager" roleLevel="60" action="review" parallel="false"/>
                <step order="2" roleName="CEO" roleLevel="100" action="sign" parallel="false"/>
            </workflow>
            """;

        workflowService.initializeWorkflow(testWorkflow, workflowXml);

        List<Task> tasks = taskRepository.findByWorkflowInstance(testWorkflow);

        // When: Пытаемся bulk approve все задачи (но пользователь не CEO)
        BulkTaskRequest request = new BulkTaskRequest();
        request.setTaskIds(tasks.stream().map(Task::getId).toList());
        request.setComment("Attempting bulk approve");

        String responseJson = mockMvc.perform(post("/api/workflow/tasks/bulk-approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        BulkOperationResponse response = objectMapper.readValue(responseJson, BulkOperationResponse.class);

        // Then: Только задача Manager одобрена, CEO задача отклонена из-за прав
        assertThat(response.getTotalTasks()).isEqualTo(2);
        assertThat(response.getSuccessfulCount()).isEqualTo(1); // Только Manager
        assertThat(response.getErrors()).hasSize(1);
        assertThat(response.getErrors().get(0)).contains("insufficient permissions");
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void testBulkRejectTasks() throws Exception {
        // Given: Workflow с несколькими задачами
        String workflowXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <workflow>
                <step order="1" roleName="Manager" roleLevel="60" action="review" parallel="false"/>
                <step order="2" roleName="Director" roleLevel="80" action="approve" parallel="false"/>
            </workflow>
            """;

        workflowService.initializeWorkflow(testWorkflow, workflowXml);

        List<Task> tasks = taskRepository.findByWorkflowInstance(testWorkflow);

        // When: Bulk reject всех задач
        BulkTaskRequest request = new BulkTaskRequest();
        request.setTaskIds(tasks.stream().map(Task::getId).toList());
        request.setComment("Bulk rejected for testing");

        String responseJson = mockMvc.perform(post("/api/workflow/tasks/bulk-reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        BulkOperationResponse response = objectMapper.readValue(responseJson, BulkOperationResponse.class);

        // Then: Проверяем результат bulk reject
        assertThat(response.getTotalTasks()).isEqualTo(2);
        assertThat(response.getSuccessfulCount()).isEqualTo(1); // Только первая задача (Manager)
        assertThat(response.getErrors()).hasSize(1); // Вторая задача не может быть отклонена
    }
}
