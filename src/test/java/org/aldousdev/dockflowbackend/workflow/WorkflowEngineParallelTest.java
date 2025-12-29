package org.aldousdev.dockflowbackend.workflow;

import org.aldousdev.dockflowbackend.auth.entity.Company;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.workflow.entity.Document;
import org.aldousdev.dockflowbackend.workflow.entity.Task;
import org.aldousdev.dockflowbackend.workflow.entity.WorkflowInstance;
import org.aldousdev.dockflowbackend.workflow.enums.TaskStatus;
import org.aldousdev.dockflowbackend.workflow.enums.WorkFlowStatus;
import org.aldousdev.dockflowbackend.workflow.repository.TaskRepository;
import org.aldousdev.dockflowbackend.workflow.engine.WorkflowEngine;
import org.aldousdev.dockflowbackend.workflow.service.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WorkflowEngineParallelTest {

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private WorkflowEngine workflowEngine;

    @Autowired
    private TaskRepository taskRepository;

    private User testUser;
    private Company testCompany;
    private Document testDocument;

    @BeforeEach
    void setUp() {
        // Создаем тестовые данные
        testUser = User.builder()
            .email("test@example.com")
            .firstName("Test")
            .lastName("User")
            .build();

        testCompany = Company.builder()
            .name("Test Company")
            .description("Test company for parallel workflows")
            .build();

        testDocument = Document.builder()
            .originalFilename("test.pdf")
            .filePath("./uploads/test.pdf")
            .company(testCompany)
            .build();
    }

    @Test
    void testParallelWorkflowInitialization() {
        // Given: XML с параллельными шагами
        String parallelWorkflowXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <workflow>
                <step order="1" roleName="Manager" roleLevel="60" action="review" parallel="false"/>
                <step order="2" roleName="Lawyer" roleLevel="70" action="review" parallel="true"/>
                <step order="2" roleName="Accountant" roleLevel="65" action="review" parallel="true"/>
                <step order="3" roleName="CEO" roleLevel="100" action="sign" parallel="false"/>
            </workflow>
            """;

        // When: Создаем workflow instance
        WorkflowInstance instance = WorkflowInstance.builder()
            .document(testDocument)
            .initiatedBy(testUser)
            .status(WorkFlowStatus.IN_PROGRESS)
            .build();

        // Инициализируем workflow
        workflowEngine.initializeWorkflow(instance, parallelWorkflowXml);

        // Then: Проверяем создание задач
        List<Task> tasks = taskRepository.findByWorkflowInstance(instance);
        assertThat(tasks).hasSize(4); // 1 + 2 + 1 задачи

        // Проверяем последовательность
        List<Task> step1Tasks = tasks.stream()
            .filter(t -> t.getStepOrder() == 1)
            .toList();
        assertThat(step1Tasks).hasSize(1);
        assertThat(step1Tasks.get(0).getRequiredRoleName()).isEqualTo("Manager");

        // Проверяем параллельные задачи на шаге 2
        List<Task> step2Tasks = tasks.stream()
            .filter(t -> t.getStepOrder() == 2)
            .toList();
        assertThat(step2Tasks).hasSize(2);

        // Проверяем роли параллельных задач
        List<String> step2Roles = step2Tasks.stream()
            .map(Task::getRequiredRoleName)
            .sorted()
            .toList();
        assertThat(step2Roles).containsExactly("Accountant", "Lawyer");

        // Проверяем шаг 3
        List<Task> step3Tasks = tasks.stream()
            .filter(t -> t.getStepOrder() == 3)
            .toList();
        assertThat(step3Tasks).hasSize(1);
        assertThat(step3Tasks.get(0).getRequiredRoleName()).isEqualTo("CEO");
    }

    @Test
    void testParallelStepCompletion() {
        // Given: Workflow с параллельными задачами
        String parallelWorkflowXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <workflow>
                <step order="1" roleName="Manager" roleLevel="60" action="review" parallel="false"/>
                <step order="2" roleName="Lawyer" roleLevel="70" action="review" parallel="true"/>
                <step order="2" roleName="Accountant" roleLevel="65" action="review" parallel="true"/>
            </workflow>
            """;

        WorkflowInstance instance = WorkflowInstance.builder()
            .document(testDocument)
            .initiatedBy(testUser)
            .status(WorkFlowStatus.IN_PROGRESS)
            .build();

        workflowService.initializeWorkflow(instance, parallelWorkflowXml);

        // When: Одобряем задачу первого шага
        List<Task> step1Tasks = taskRepository.findByWorkflowInstance(instance).stream()
            .filter(t -> t.getStepOrder() == 1)
            .toList();
        Task step1Task = step1Tasks.get(0);
        workflowEngine.approveTask(step1Task, testUser, "Approved");

        // Then: Проверяем, что workflow не завершился (ждет параллельные задачи)
        List<Task> allTasks = taskRepository.findByWorkflowInstance(instance);
        long pendingTasks = allTasks.stream()
            .filter(t -> t.getStatus() == TaskStatus.PENDING)
            .count();

        // Должны остаться 2 pending задачи на шаге 2
        assertThat(pendingTasks).isEqualTo(2);

        // Workflow все еще в процессе
        assertThat(instance.getStatus()).isEqualTo(WorkFlowStatus.IN_PROGRESS);
    }

    @Test
    void testSequentialWorkflowInitialization() {
        // Given: XML с последовательными шагами
        String sequentialWorkflowXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <workflow>
                <step order="1" roleName="Manager" roleLevel="60" action="review" parallel="false"/>
                <step order="2" roleName="Director" roleLevel="80" action="approve" parallel="false"/>
                <step order="3" roleName="CEO" roleLevel="100" action="sign" parallel="false"/>
            </workflow>
            """;

        // When: Создаем workflow instance
        WorkflowInstance instance = WorkflowInstance.builder()
            .document(testDocument)
            .initiatedBy(testUser)
            .status(WorkFlowStatus.IN_PROGRESS)
            .build();

        workflowEngine.initializeWorkflow(instance, sequentialWorkflowXml);

        // Then: Проверяем создание задач
        List<Task> tasks = taskRepository.findByWorkflowInstance(instance);
        assertThat(tasks).hasSize(3); // Только по одной задаче на шаг

        // Проверяем последовательность ролей
        List<String> roles = tasks.stream()
            .sorted((a, b) -> Integer.compare(a.getStepOrder(), b.getStepOrder()))
            .map(Task::getRequiredRoleName)
            .toList();

        assertThat(roles).containsExactly("Manager", "Director", "CEO");
    }
}
