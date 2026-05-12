package org.aldousdev.dockflowbackend.dashboard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.dashboard.dto.response.DashboardActivityResponse;
import org.aldousdev.dockflowbackend.dashboard.dto.response.DashboardStatsResponse;
import org.aldousdev.dockflowbackend.workflow.entity.WorkflowAuditLog;
import org.aldousdev.dockflowbackend.workflow.enums.WorkFlowStatus;
import org.aldousdev.dockflowbackend.workflow.repository.DocumentRepository;
import org.aldousdev.dockflowbackend.workflow.repository.WorkflowAuditLogRepository;
import org.aldousdev.dockflowbackend.workflow.repository.WorkflowInstanceRepository;
import org.aldousdev.dockflowbackend.workflow.service.WorkflowService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {
    private final DocumentRepository documentRepository;
    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final WorkflowAuditLogRepository auditLogRepository;
    private final WorkflowService workflowService;

    @Transactional(readOnly = true)
    public DashboardStatsResponse getStats(Long companyId, User user) {
        long totalDocs = documentRepository.countByCompanyId(companyId);
        long activeWorkflows = workflowInstanceRepository.countByDocumentCompanyIdAndStatus(companyId, WorkFlowStatus.IN_PROGRESS);
        long pendingTasks = (long) workflowService.getUserPendingTasks(user, companyId).size();

        return DashboardStatsResponse.builder()
                .totalDocuments(totalDocs)
                .activeWorkflows(activeWorkflows)
                .pendingTasks(pendingTasks)
                .build();
    }

    @Transactional(readOnly = true)
    public List<DashboardActivityResponse> getRecentActivities(Long companyId) {
        return auditLogRepository.findByWorkflowInstanceDocumentCompanyIdOrderByCreatedAtDesc(companyId, PageRequest.of(0, 10))
                .map(this::mapToActivityResponse)
                .getContent();
    }

    private DashboardActivityResponse mapToActivityResponse(WorkflowAuditLog log) {
        return DashboardActivityResponse.builder()
                .id(log.getId())
                .actionType(log.getActionType())
                .description(log.getDescription())
                .performedBy(log.getPerformedBy() != null ? log.getPerformedBy().getFirstName() + " " + log.getPerformedBy().getLastName() : "System")
                .createdAt(log.getCreatedAt())
                .documentName(log.getWorkflowInstance().getDocument().getOriginalFilename())
                .build();
    }
}
