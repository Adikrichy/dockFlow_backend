package org.aldousdev.dockflowbackend.workflow.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.auth.components.RequiresRoleLevel;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.auth.security.JWTService;
import org.aldousdev.dockflowbackend.auth.service.UserService;
import org.aldousdev.dockflowbackend.workflow.components.CanStartWorkflow;
import org.aldousdev.dockflowbackend.workflow.dto.request.BulkTaskRequest;
import org.aldousdev.dockflowbackend.workflow.dto.request.CreateWorkflowTemplateRequest;
import org.aldousdev.dockflowbackend.workflow.dto.request.UpdateWorkflowTemplateRequest;
import org.aldousdev.dockflowbackend.workflow.dto.request.TaskApprovalRequest;
import org.aldousdev.dockflowbackend.workflow.dto.request.UpdateWorkflowPermissionRequest;
import org.aldousdev.dockflowbackend.workflow.dto.response.BulkOperationResponse;
import org.aldousdev.dockflowbackend.workflow.dto.response.TaskResponse;
import org.aldousdev.dockflowbackend.workflow.dto.response.WorkflowAuditLogResponse;
import org.aldousdev.dockflowbackend.workflow.dto.response.WorkflowInstanceResponse;
import org.aldousdev.dockflowbackend.workflow.dto.response.WorkflowTemplateResponse;
import org.aldousdev.dockflowbackend.workflow.entity.Task;
import org.aldousdev.dockflowbackend.workflow.service.BulkWorkflowService;
import org.aldousdev.dockflowbackend.workflow.service.WorkflowService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Workflow", description = "Управление workflow процессами для документов")
public class WorkflowController {
    private final WorkflowService workflowService;
    private final BulkWorkflowService bulkWorkflowService;
    private final UserService userService;
    private final JWTService jwtService;

    /**
     * POST /api/workflow/template - создать новый workflow template
     */
    @PostMapping("/template")
    @RequiresRoleLevel(60) // Manager и выше
    @Operation(summary = "Создать новый workflow шаблон", 
            description = "Создает новый workflow шаблон с XML определением и routing правилами. " +
                    "XML должен содержать <workflow> элемент с <step> подэлементами. " +
                    "Требует роль Manager или выше.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Workflow шаблон успешно создан",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = WorkflowTemplateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Некорректный XML или отсутствуют обязательные поля"),
            @ApiResponse(responseCode = "401", description = "Требуется аутентификация"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав доступа")
    })
    public ResponseEntity<WorkflowTemplateResponse> createTemplate(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Данные для создания workflow шаблона")
            @RequestBody CreateWorkflowTemplateRequest request,
            Authentication authentication) {
        
        log.info("Creating workflow template: {}", request.getName());
        User user = userService.getUserByEmail(authentication.getName());
        
        WorkflowTemplateResponse template = workflowService.createTemplate(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(template);
    }

    /**
     * GET /api/workflow/company/{companyId}/templates - получить все templates компании
     */
    @GetMapping("/company/{companyId}/templates")
    @RequiresRoleLevel(10) // Все authenticated пользователи
    @Operation(summary = "Получить все workflow шаблоны компании", 
            description = "Возвращает список всех workflow шаблонов для указанной компании")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список шаблонов успешно получен",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = WorkflowTemplateResponse.class))),
            @ApiResponse(responseCode = "401", description = "Требуется аутентификация"),
            @ApiResponse(responseCode = "404", description = "Компания не найдена")
    })
    public ResponseEntity<List<WorkflowTemplateResponse>> getCompanyTemplates(
            @Parameter(description = "ID компании", required = true)
            @PathVariable Long companyId,
            Authentication authentication) {

        User user = userService.getUserByEmail(authentication.getName());

        log.info("Fetching templates for company: {}", companyId);
        List<WorkflowTemplateResponse> templates = workflowService.getCompanyTemplates(companyId, user);
        return ResponseEntity.ok(templates);
    }

    /**
     * GET /api/workflow/template/{templateId} - получить template по ID
     */
    @GetMapping("/template/{templateId}")
    @RequiresRoleLevel(10)
    @Operation(summary = "Получить workflow шаблон по ID", 
            description = "Возвращает полную информацию о workflow шаблоне, включая XML и routing правила")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Шаблон успешно получен",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = WorkflowTemplateResponse.class))),
            @ApiResponse(responseCode = "401", description = "Требуется аутентификация"),
            @ApiResponse(responseCode = "404", description = "Шаблон не найден")
    })
    public ResponseEntity<WorkflowTemplateResponse> getTemplate(
            @Parameter(description = "ID workflow шаблона", required = true)
            @PathVariable Long templateId) {
        
        log.info("Fetching template: {}", templateId);
        WorkflowTemplateResponse template = workflowService.getTemplate(templateId);
        return ResponseEntity.ok(template);
    }

    /**
     * POST /api/workflow/{templateId}/start - запустить workflow для документа
     * Body: { "documentId": 123 }
     */
    @PostMapping("/{templateId}/start")
    @CanStartWorkflow
    @Operation(summary = "Запустить workflow для документа", 
            description = "Инициирует новый workflow процесс для документа, используя указанный шаблон. " +
                    "Требуется разрешение на запуск этого workflow template.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Workflow успешно запущен",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = WorkflowInstanceResponse.class))),
            @ApiResponse(responseCode = "400", description = "Документ уже имеет активный workflow"),
            @ApiResponse(responseCode = "401", description = "Требуется аутентификация"),
            @ApiResponse(responseCode = "404", description = "Шаблон или документ не найдены")
    })
    public ResponseEntity<WorkflowInstanceResponse> startWorkflow(
            @Parameter(description = "ID workflow шаблона", required = true)
            @PathVariable Long templateId,
            @Parameter(description = "ID документа", required = true)
            @RequestParam Long documentId,
            Authentication authentication) {
        
        log.info("Starting workflow {} for document {}", templateId, documentId);
        User user = userService.getUserByEmail(authentication.getName());
        
        WorkflowInstanceResponse instance = workflowService.startWorkflow(documentId, templateId, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(instance);
    }

    /**
     * GET /api/workflow/instance/{instanceId} - получить workflow instance с tasks
     */
    @GetMapping("/instance/{instanceId}")
    @RequiresRoleLevel(10)
    @Operation(summary = "Получить workflow instance с задачами", 
            description = "Возвращает полную информацию о workflow instance, включая все его tasks и текущий статус")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Workflow instance успешно получен",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = WorkflowInstanceResponse.class))),
            @ApiResponse(responseCode = "401", description = "Требуется аутентификация"),
            @ApiResponse(responseCode = "404", description = "Workflow instance не найден")
    })
    public ResponseEntity<WorkflowInstanceResponse> getWorkflowInstance(
            @Parameter(description = "ID workflow instance", required = true)
            @PathVariable Long instanceId) {
        
        log.info("Fetching workflow instance: {}", instanceId);
        WorkflowInstanceResponse instance = workflowService.getWorkflowInstance(instanceId);
        return ResponseEntity.ok(instance);
    }

    /**
     * GET /api/workflow/document/{documentId}/tasks - получить все tasks документа
     */
    @GetMapping("/document/{documentId}/tasks")
    @RequiresRoleLevel(10)
    @Operation(summary = "Получить все задачи документа", 
            description = "Возвращает все tasks (одобрение, отклонение) для указанного документа во всех workflow instances")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список задач успешно получен",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TaskResponse.class))),
            @ApiResponse(responseCode = "401", description = "Требуется аутентификация"),
            @ApiResponse(responseCode = "404", description = "Документ не найден")
    })
    public ResponseEntity<List<TaskResponse>> getDocumentTasks(
            @Parameter(description = "ID документа", required = true)
            @PathVariable Long documentId) {
        
        log.info("Fetching tasks for document: {}", documentId);
        List<TaskResponse> tasks = workflowService.getDocumentTasks(documentId);
        return ResponseEntity.ok(tasks);
    }

    /**
     * GET /api/workflow/my-tasks - получить мои pending tasks
     */
    @GetMapping("/my-tasks")
    @RequiresRoleLevel(10)
    @Operation(summary = "Получить мои ожидающие задачи",
            description = "Возвращает все tasks, требующие одобрения от текущего пользователя. " +
                    "Это основной endpoint для личного dashboard утверждающего")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список задач успешно получен",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TaskResponse.class))),
            @ApiResponse(responseCode = "401", description = "Требуется аутентификация")
    })
    public ResponseEntity<List<TaskResponse>> getMyPendingTasks(
            Authentication authentication,
            HttpServletRequest request) {

        log.info("Fetching pending tasks for user: {}", authentication.getName());
        User user = userService.getUserByEmail(authentication.getName());

        // ============ ИСПРАВЛЕНИЕ: читаем companyId из cookie jwtWithCompany ============
        Long companyId = null;

        // Получаем cookie jwtWithCompany
        if (request.getCookies() != null) {
            String jwtWithCompany = java.util.Arrays.stream(request.getCookies())
                    .filter(cookie -> "jwtWithCompany".equals(cookie.getName()))
                    .map(jakarta.servlet.http.Cookie::getValue)
                    .findFirst()
                    .orElse(null);

            // Если есть cookie - извлекаем companyId
            if (jwtWithCompany != null && jwtService.isTokenValid(jwtWithCompany)) {
                companyId = jwtService.extractCompanyId(jwtWithCompany);
            }
        }

        // Если нет jwtWithCompany cookie - возвращаем пустой список
        if (companyId == null) {
            log.warn("No active company for user: {}", authentication.getName());
            return ResponseEntity.ok(List.of());
        }
        // ============ КОНЕЦ ИСПРАВЛЕНИЯ ============

        List<TaskResponse> tasks = workflowService.getUserPendingTasks(user, companyId);
        return ResponseEntity.ok(tasks);
    }

    /**
     * POST /api/workflow/task/{taskId}/approve - одобрить task
     */
    @PostMapping("/task/{taskId}/approve")
    @RequiresRoleLevel(10)
    @Operation(summary = "Одобрить задачу", 
            description = "Одобряет task и либо переходит на следующий step workflow, " +
                    "либо завершает workflow если это последний task. " +
                    "Отправляет email уведомления и логирует действие в audit trail")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Task успешно одобрена",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TaskResponse.class))),
            @ApiResponse(responseCode = "400", description = "Task уже одобрена или отклонена"),
            @ApiResponse(responseCode = "401", description = "Требуется аутентификация"),
            @ApiResponse(responseCode = "403", description = "У вас нет прав одобрить эту task"),
            @ApiResponse(responseCode = "404", description = "Task не найдена")
    })
    public ResponseEntity<TaskResponse> approveTask(
            @Parameter(description = "ID task", required = true)
            @PathVariable Long taskId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Комментарий к одобрению")
            @RequestBody TaskApprovalRequest request,
            Authentication authentication) {
        
        log.info("Approving task: {}", taskId);
        User user = userService.getUserByEmail(authentication.getName());
        
        TaskResponse task = workflowService.approveTask(taskId, user, request.getComment());
        return ResponseEntity.ok(task);
    }

    /**
     * POST /api/workflow/task/{taskId}/reject - отклонить task
     */
    @PostMapping("/task/{taskId}/reject")
    @RequiresRoleLevel(10)
    @Operation(summary = "Отклонить задачу", 
            description = "Отклоняет task и применяет правила маршрутизации для возврата на предыдущий шаг " +
                    "или завершения workflow. Отправляет email уведомления и логирует действие в audit trail")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Task успешно отклонена",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TaskResponse.class))),
            @ApiResponse(responseCode = "400", description = "Task уже одобрена или отклонена"),
            @ApiResponse(responseCode = "401", description = "Требуется аутентификация"),
            @ApiResponse(responseCode = "403", description = "У вас нет прав отклонить эту task"),
            @ApiResponse(responseCode = "404", description = "Task не найдена")
    })
    public ResponseEntity<TaskResponse> rejectTask(
            @Parameter(description = "ID task", required = true)
            @PathVariable Long taskId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Комментарий к отклонению")
            @RequestBody TaskApprovalRequest request,
            Authentication authentication) {
        
        log.info("Rejecting task: {}", taskId);
        User user = userService.getUserByEmail(authentication.getName());
        
        TaskResponse task = workflowService.rejectTask(taskId, user, request.getComment());
        return ResponseEntity.ok(task);
    }

    /**
     * GET /api/workflow/instance/{instanceId}/audit - получить audit историю
     */
    @GetMapping("/instance/{instanceId}/audit")
    @RequiresRoleLevel(10)
    @Operation(summary = "Получить audit историю workflow", 
            description = "Возвращает полный audit trail для workflow instance, включая все действия, " +
                    "кто их выполнил, когда и с какого IP адреса. Используется для compliance и отчетности")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Audit лог успешно получен",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = WorkflowAuditLogResponse.class))),
            @ApiResponse(responseCode = "401", description = "Требуется аутентификация"),
            @ApiResponse(responseCode = "404", description = "Workflow instance не найден")
    })
    public ResponseEntity<List<WorkflowAuditLogResponse>> getWorkflowAudit(
            @Parameter(description = "ID workflow instance", required = true)
            @PathVariable Long instanceId) {
        
        log.info("Fetching audit log for workflow instance: {}", instanceId);
        List<WorkflowAuditLogResponse> auditLog = workflowService.getWorkflowAuditLog(instanceId);
        return ResponseEntity.ok(auditLog);
    }

    /**
     * GET /api/workflow/company/{companyId}/tasks - получить все задачи компании для Kanban
     */
    @GetMapping("/company/{companyId}/tasks")
    @RequiresRoleLevel(10) // Manager и выше
    @Operation(summary = "Получить все задачи компании (для Kanban)", 
            description = "Возвращает список всех задач всех workflow процессов компании. Испольуется для Kanban доски.")
    public ResponseEntity<List<TaskResponse>> getCompanyTasks(@PathVariable Long companyId) {
        log.info("Fetching all tasks for company: {}", companyId);
        return ResponseEntity.ok(workflowService.getCompanyTasks(companyId));
    }

    /**
     * POST /api/workflow/task/{taskId}/assign/{userId} - назначить задачу
     */
    @PostMapping("/task/{taskId}/assign/{userId}")
    @RequiresRoleLevel(60)
    @Operation(summary = "Назначить задачу пользователю", 
            description = "Устанавливает исполнителя для задачи. Доступно менеджерам.")
    public ResponseEntity<TaskResponse> assignTask(@PathVariable Long taskId, @PathVariable Long userId) {
        return ResponseEntity.ok(workflowService.assignTask(taskId, userId));
    }

    /**
     * PUT /api/workflow/task/{taskId}/status - обновить статус задачи
     */
    @PutMapping("/task/{taskId}/status")
    @RequiresRoleLevel(60)
    @Operation(summary = "Обновить статус задачи", 
            description = "Меняет статус задачи (перемещение по Kanban колонкам).")
    public ResponseEntity<TaskResponse> updateTaskStatus(
            @PathVariable Long taskId, 
            @RequestParam org.aldousdev.dockflowbackend.workflow.enums.TaskStatus status) {
        return ResponseEntity.ok(workflowService.updateTaskStatus(taskId, status));
    }

    /**
     * POST /api/workflow/tasks/bulk-approve - массовое одобрение задач
     */
    @PostMapping("/tasks/bulk-approve")
    @RequiresRoleLevel(10) // Все аутентифицированные пользователи
    @Operation(summary = "Массовое одобрение задач",
            description = "Одновременно одобряет несколько задач. Проверяет права доступа для каждой задачи.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Массовое одобрение выполнено",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BulkOperationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Некоторые задачи не могут быть одобрены"),
            @ApiResponse(responseCode = "401", description = "Требуется аутентификация"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав для некоторых задач")
    })
    public ResponseEntity<BulkOperationResponse> bulkApproveTasks(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Данные для массового одобрения")
            @RequestBody BulkTaskRequest request,
            Authentication authentication) {

        log.info("Bulk approving {} tasks", request.getTaskIds().size());
        User user = userService.getUserByEmail(authentication.getName());

        // Получаем текущую компанию пользователя
        var companyId = user.getMemberships().get(0).getCompany().getId();

        // Получаем задачи с проверкой прав доступа
        List<Task> tasks = bulkWorkflowService.getTasksWithAccessCheck(
            request.getTaskIds(), user, companyId);

        BulkOperationResponse response = bulkWorkflowService.bulkApprove(
            tasks, user, request.getComment());

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/workflow/tasks/bulk-reject - массовое отклонение задач
     */
    @PostMapping("/tasks/bulk-reject")
    @RequiresRoleLevel(10) // Все аутентифицированные пользователи
    @Operation(summary = "Массовое отклонение задач",
            description = "Одновременно отклоняет несколько задач. Проверяет права доступа для каждой задачи.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Массовое отклонение выполнено",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BulkOperationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Некоторые задачи не могут быть отклонены"),
            @ApiResponse(responseCode = "401", description = "Требуется аутентификация"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав для некоторых задач")
    })
    public ResponseEntity<BulkOperationResponse> bulkRejectTasks(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Данные для массового отклонения")
            @RequestBody BulkTaskRequest request,
            Authentication authentication) {

        log.info("Bulk rejecting {} tasks", request.getTaskIds().size());
        User user = userService.getUserByEmail(authentication.getName());

        // Получаем текущую компанию пользователя
        var companyId = user.getMemberships().get(0).getCompany().getId();

        // Получаем задачи с проверкой прав доступа
        List<Task> tasks = bulkWorkflowService.getTasksWithAccessCheck(
            request.getTaskIds(), user, companyId);

        BulkOperationResponse response = bulkWorkflowService.bulkReject(
            tasks, user, request.getComment());

        return ResponseEntity.ok(response);
    }
    @PatchMapping("/template/{templateId}/permissions")
    @RequiresRoleLevel(100)
    @Operation(summary = "Обновить разрешения на запуск workflow",
            description = "Позволяет изменить список уровней ролей, которым разрешено запускать данный workflow шаблон. " +
                    "Доступно создателю шаблона или CEO компании.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Разрешения успешно обновлены"),
            @ApiResponse(responseCode = "403", description = "Нет прав на изменение разрешений"),
            @ApiResponse(responseCode = "404", description = "Шаблон не найден")
    })
    public ResponseEntity<WorkflowTemplateResponse> updateWorkflowPermissions(
            @PathVariable Long templateId,
            @RequestBody UpdateWorkflowPermissionRequest request,
            Authentication authentication
            ){
        User user = userService.getUserByEmail(authentication.getName());

        WorkflowTemplateResponse update = workflowService.updateAllowedRoleLevels(templateId,
                request.getAllowedRoleLevels(),user);
        return ResponseEntity.ok(update);
    }

    /**
     * PUT /api/workflow/template/{templateId} - обновить шаблон
     */
    @PutMapping("/template/{templateId}")
    @RequiresRoleLevel(60)
    @Operation(summary = "Обновить workflow шаблон",
            description = "Обновляет существующий workflow шаблон. Требует роль Manager или выше.")
    public ResponseEntity<WorkflowTemplateResponse> updateTemplate(
            @PathVariable Long templateId,
            @RequestBody UpdateWorkflowTemplateRequest request,
            Authentication authentication) {

        log.info("Updating workflow template: {}", templateId);
        User user = userService.getUserByEmail(authentication.getName());

        WorkflowTemplateResponse template = workflowService.updateTemplate(templateId, request, user);
        return ResponseEntity.ok(template);
    }

    /**
     * DELETE /api/workflow/template/{templateId} - удалить шаблон
     */
    @DeleteMapping("/template/{templateId}")
    @RequiresRoleLevel(60)
    @Operation(summary = "Удалить workflow шаблон",
            description = "Удаляет workflow шаблон (soft delete). Требует роль Manager или выше.")
    public ResponseEntity<Void> deleteTemplate(
            @PathVariable Long templateId,
            Authentication authentication) {

        log.info("Deleting workflow template: {}", templateId);
        User user = userService.getUserByEmail(authentication.getName());

        workflowService.deleteTemplate(templateId, user);
        return ResponseEntity.noContent().build();
    }
}
