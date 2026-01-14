package org.aldousdev.dockflowbackend.reports.service;

import org.aldousdev.dockflowbackend.auth.repository.*;
import org.aldousdev.dockflowbackend.reports.dto.ReportDataDTO;
import org.aldousdev.dockflowbackend.reports.dto.ReportFiltersDTO;
import org.aldousdev.dockflowbackend.workflow.entity.Document;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.workflow.entity.Task;
import org.aldousdev.dockflowbackend.workflow.repository.DocumentRepository;
import org.aldousdev.dockflowbackend.workflow.repository.TaskRepository;
import org.aldousdev.dockflowbackend.workflow.repository.DocumentVersionRepository;
import org.aldousdev.dockflowbackend.reports.entity.SavedReport;
import org.aldousdev.dockflowbackend.reports.repository.SavedReportRepository;
import org.aldousdev.dockflowbackend.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime; // Added import
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportsService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final SecurityAuditRepository securityAuditRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final CompanyRoleEntityRepository companyRoleEntityRepository;
    private final MembershipRepository membershipRepository;
    private final SavedReportRepository savedReportRepository;
    private final CompanyRepository companyRepository;
    private final AuthService authService;

    public void updateRolePermissions(Long roleId, Long companyId, Boolean canViewReports) {
        var role = companyRoleEntityRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found"));
        
        if (!role.getCompany().getId().equals(companyId)) {
            throw new RuntimeException("Role does not belong to this company");
        }
        
        role.setCanViewReports(canViewReports);
        companyRoleEntityRepository.save(role);
    }
    
    public boolean hasReportAccess(Long userId, Long companyId) {
        return membershipRepository.findByCompanyIdAndUserId(companyId, userId)
                .map(membership -> {
                     if (membership.getRole() != null) {
                         if ("CEO".equalsIgnoreCase(membership.getRole().getName())) {
                             return true;
                         }
                         return Boolean.TRUE.equals(membership.getRole().getCanViewReports());
                     }
                     return false;
                })
                .orElse(false);
    }

    public ReportDataDTO getReportSummary(ReportFiltersDTO filters) {
        log.info("Generating report summary with filters: {}", filters);

        LocalDateTime startDate = getStartDate(filters.getTimeRange());
        LocalDateTime endDate = LocalDateTime.now();
        Long companyId = parseCompanyId(filters.getCompany());

        List<Document> documents;
        List<Task> allTasksInRange;
        List<Task> completedTasksInRange;

        if (companyId != null) {
            documents = documentRepository.findByCompanyIdAndUploadedAtBetween(companyId, startDate, endDate);
            allTasksInRange = taskRepository.findByCompanyIdAndCreatedAtBetween(companyId, startDate, endDate);
            completedTasksInRange = taskRepository.findByCompanyIdAndCompletedAtBetween(companyId, startDate, endDate);
        } else {
            documents = documentRepository.findByUploadedAtBetween(startDate, endDate);
            allTasksInRange = taskRepository.findByCreatedAtBetween(startDate, endDate);
            completedTasksInRange = taskRepository.findByCompletedAtBetween(startDate, endDate);
        }

        long activeUsersCount = securityAuditRepository.countDistinctByTimestampAfter(startDate);
        
        long totalVersions;
        if (companyId != null) {
            totalVersions = documentVersionRepository.countByCompanyIdAndCreatedAtBetween(companyId, startDate, endDate);
        } else {
            totalVersions = documentVersionRepository.countByCreatedAtBetween(startDate, endDate);
        }
        
        ReportDataDTO reportData = new ReportDataDTO();
        
        // Basic statistics for the period
        reportData.setTotalDocuments((long) documents.size());
        reportData.setTotalVersions(totalVersions);
        
        // "Pending" are documents uploaded in this period that are still pending
        reportData.setPendingDocuments((long) documents.stream()
                .filter(d -> "PENDING".equals(d.getStatus().name()))
                .count());
        
        // "Approved" and "Rejected" are based on activity in this period (tasks completed)
        // We count unique documents approved/rejected to avoid over-counting multi-step workflows
        reportData.setApprovedDocuments(completedTasksInRange.stream()
                .filter(t -> org.aldousdev.dockflowbackend.workflow.enums.TaskStatus.APPROVED.equals(t.getStatus()))
                .map(t -> t.getWorkflowInstance().getDocument().getId())
                .distinct()
                .count());
        
        reportData.setRejectedDocuments(completedTasksInRange.stream()
                .filter(t -> org.aldousdev.dockflowbackend.workflow.enums.TaskStatus.REJECTED.equals(t.getStatus()))
                .map(t -> t.getWorkflowInstance().getDocument().getId())
                .distinct()
                .count());

        // Average processing time in hours (using completedAt)
        double avgProcessingTime = completedTasksInRange.stream()
                .mapToLong(t -> ChronoUnit.HOURS.between(t.getCreatedAt(), t.getCompletedAt()))
                .average()
                .orElse(0.0);
        reportData.setAverageProcessingTime(Math.round(avgProcessingTime * 10.0) / 10.0);

        // User statistics
        reportData.setTotalUsers(userRepository.count());
        reportData.setActiveUsers(activeUsersCount);

        // Weekly data
        reportData.setWeeklyData(getWeeklyData(filters));
        
        // User activity
        reportData.setUserActivity(getUserActivityInternal(filters));
        
        // Document types
        reportData.setDocumentTypes(getDocumentTypesInternal(filters));

        return reportData;
    }

    public List<Map<String, Object>> getWeeklyActivity(ReportFiltersDTO filters) {
        return getWeeklyData(filters);
    }

    public List<Map<String, Object>> getWeeklyActivity(String timeRange) {
        ReportFiltersDTO filters = new ReportFiltersDTO();
        filters.setTimeRange(timeRange);
        return getWeeklyData(filters);
    }
    
    private List<Map<String, Object>> getWeeklyData(ReportFiltersDTO filters) {
        LocalDateTime startDate = getStartDate(filters.getTimeRange());
        LocalDateTime endDate = LocalDateTime.now();
        Long companyId = parseCompanyId(filters.getCompany());
        
        List<Document> uploadedDocuments;
        List<Task> completedTasks;

        if (companyId != null) {
            uploadedDocuments = documentRepository.findByCompanyIdAndUploadedAtBetween(companyId, startDate, endDate);
            completedTasks = taskRepository.findByCompanyIdAndCompletedAtBetween(companyId, startDate, endDate);
        } else {
            uploadedDocuments = documentRepository.findByUploadedAtBetween(startDate, endDate);
            completedTasks = taskRepository.findByCompletedAtBetween(startDate, endDate);
        }

        // Group documents by date (for "pending/new" activity)
        Map<LocalDate, List<Document>> docsByDate = uploadedDocuments.stream()
            .collect(Collectors.groupingBy(d -> d.getUploadedAt().toLocalDate()));

        // Group tasks by date (for "approved/rejected" activity)
        Map<LocalDate, List<Task>> tasksByDate = completedTasks.stream()
            .collect(Collectors.groupingBy(t -> t.getCompletedAt().toLocalDate()));

        List<Map<String, Object>> weeklyData = new ArrayList<>();
        long daysDiff = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        
        for (int i = 0; i < daysDiff; i++) {
            LocalDate date = startDate.plusDays(i).toLocalDate();
            if (date.isAfter(endDate.toLocalDate())) break;
            
            List<Document> dayDocs = docsByDate.getOrDefault(date, Collections.emptyList());
            List<Task> dayTasks = tasksByDate.getOrDefault(date, Collections.emptyList());
            
            Map<String, Object> dayData = new HashMap<>();
            String dayLabel = date.format(DateTimeFormatter.ofPattern("EEE")); 
            
            dayData.put("day", dayLabel);
            // Approved and Rejected are based on TASKS completed that day
            dayData.put("approved", dayTasks.stream()
                    .filter(t -> org.aldousdev.dockflowbackend.workflow.enums.TaskStatus.APPROVED.equals(t.getStatus()))
                    .count());
            dayData.put("rejected", dayTasks.stream()
                    .filter(t -> org.aldousdev.dockflowbackend.workflow.enums.TaskStatus.REJECTED.equals(t.getStatus()))
                    .count());
            // Pending activity is based on NEW DOCUMENTS uploaded that day
            dayData.put("pending", dayDocs.size());
            
            weeklyData.add(dayData);
        }
        
        return weeklyData;
    }

    public List<Map<String, Object>> getUserActivity(ReportFiltersDTO filters) {
        return getUserActivityInternal(filters);
    }

    public List<Map<String, Object>> getUserActivity(String timeRange) {
        ReportFiltersDTO filters = new ReportFiltersDTO();
        filters.setTimeRange(timeRange);
        return getUserActivityInternal(filters);
    }

    private List<Map<String, Object>> getUserActivityInternal(ReportFiltersDTO filters) {
        LocalDateTime startDate = getStartDate(filters.getTimeRange());
        LocalDateTime endDate = LocalDateTime.now();
        Long companyId = parseCompanyId(filters.getCompany());

        List<Task> completedTasks = taskRepository.findByCompletedAtBetween(startDate, endDate);
        if (companyId != null) {
            completedTasks = completedTasks.stream()
                .filter(t -> t.getWorkflowInstance().getDocument().getCompany().getId().equals(companyId))
                .collect(Collectors.toList());
        }

        Map<User, List<Task>> byUser = completedTasks.stream()
            .filter(t -> t.getCompletedBy() != null)
            .collect(Collectors.groupingBy(Task::getCompletedBy));

        List<Map<String, Object>> userActivity = new ArrayList<>();
        long totalTasks = completedTasks.size();

        for (Map.Entry<User, List<Task>> entry : byUser.entrySet()) {
            User user = entry.getKey();
            List<Task> tasks = entry.getValue();
            
            double avgTime = tasks.stream()
                .mapToLong(t -> ChronoUnit.SECONDS.between(t.getCreatedAt(), t.getCompletedAt()))
                .average()
                .orElse(0);

            Map<String, Object> userData = new HashMap<>();
            userData.put("userName", user.getFirstName() + " " + user.getLastName());
            userData.put("documentsProcessed", tasks.size());
            userData.put("percentage", totalTasks > 0 ? Math.round((double)tasks.size() / totalTasks * 1000.0) / 10.0 : 0);
            
            long hours = (long) avgTime / 3600;
            long minutes = ((long) avgTime % 3600) / 60;
            long seconds = (long) avgTime % 60;
            
            userData.put("processingTime", String.format("%02d:%02d:%02d", hours, minutes, seconds));
            userActivity.add(userData);
        }
        
        return userActivity;
    }

    public List<Map<String, Object>> getDocumentTypes(ReportFiltersDTO filters) {
        return getDocumentTypesInternal(filters);
    }

    public List<Map<String, Object>> getDocumentTypes(String timeRange) {
        ReportFiltersDTO filters = new ReportFiltersDTO();
        filters.setTimeRange(timeRange);
        return getDocumentTypesInternal(filters);
    }
    
    private List<Map<String, Object>> getDocumentTypesInternal(ReportFiltersDTO filters) {
        LocalDateTime startDate = getStartDate(filters.getTimeRange());
        LocalDateTime endDate = LocalDateTime.now();
        Long companyId = parseCompanyId(filters.getCompany());

        List<Document> documents = documentRepository.findByUploadedAtBetween(startDate, endDate);
        if (companyId != null) {
            documents = documents.stream()
                .filter(d -> d.getCompany().getId().equals(companyId))
                .collect(Collectors.toList());
        }

        Map<String, Long> byType = documents.stream()
            .collect(Collectors.groupingBy(d -> d.getDocumentType().name(), Collectors.counting()));

        List<Map<String, Object>> documentTypes = new ArrayList<>();
        String[] colors = {"#3b82f6", "#10b981", "#f59e0b", "#8b5cf6", "#ec4899", "#6366f1"};
        int colorIdx = 0;

        for (Map.Entry<String, Long> entry : byType.entrySet()) {
            Map<String, Object> typeData = new HashMap<>();
            typeData.put("type", entry.getKey());
            typeData.put("count", entry.getValue());
            typeData.put("color", colors[colorIdx % colors.length]);
            documentTypes.add(typeData);
            colorIdx++;
        }
        
        return documentTypes;
    }

    public ByteArrayResource exportReport(ReportFiltersDTO filters, String format) {
        String content = generateReportContent(filters, format);
        return new ByteArrayResource(content.getBytes());
    }

    public String saveReport(String name, ReportFiltersDTO filters) {
        Long companyId = parseCompanyId(filters.getCompany());
        var company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));
        
        User currentUser = authService.getCurrentUser();
        
        SavedReport report = SavedReport.builder()
                .name(name)
                .timeRange(filters.getTimeRange())
                .team(filters.getTeam())
                .tag(filters.getTag())
                .company(company)
                .createdBy(currentUser)
                .build();
        
        report = savedReportRepository.save(report);
        return String.valueOf(report.getId());
    }

    public List<Map<String, Object>> getSavedReports(Long companyId) {
        return savedReportRepository.findByCompanyId(companyId).stream()
                .map(report -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", report.getId());
                    map.put("name", report.getName());
                    map.put("timeRange", report.getTimeRange());
                    map.put("createdAt", report.getCreatedAt());
                    map.put("createdBy", report.getCreatedBy().getFirstName() + " " + report.getCreatedBy().getLastName());
                    return map;
                })
                .collect(Collectors.toList());
    }

    public Map<String, Object> getDashboardStats() {
        // Reusing getReportSummary logic partially or creating simple stats
        Map<String, Object> stats = new HashMap<>();
        
        LocalDateTime weekAgo = LocalDateTime.now().minus(7, ChronoUnit.DAYS);
        List<Document> recentDocs = documentRepository.findByUploadedAtAfter(weekAgo);
        
        stats.put("totalDocuments", documentRepository.count());
        stats.put("recentDocuments", recentDocs.size());
        stats.put("pendingDocuments", recentDocs.stream()
                .filter(d -> "PENDING".equals(d.getStatus().name()))
                .count());
        stats.put("totalUsers", userRepository.count());
        stats.put("activeWorkflows", 5L); 
        
        return stats;
    }

    private LocalDateTime getStartDate(String timeRange) {
        LocalDateTime now = LocalDateTime.now();
        if (timeRange == null) return now.minus(7, ChronoUnit.DAYS);
        
        return switch (timeRange.toLowerCase()) {
            case "lastweek" -> now.minus(14, ChronoUnit.DAYS);
            case "thismonth" -> now.withDayOfMonth(1).with(LocalTime.MIN);
            case "thisyear" -> now.withDayOfYear(1).with(LocalTime.MIN);
            default -> now.minus(7, ChronoUnit.DAYS); // thisWeek
        };
    }
    
    private Long parseCompanyId(String company) {
        if (company == null || company.isBlank()) return null;
        try {
            return Long.valueOf(company);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String generateReportContent(ReportFiltersDTO filters, String format) {
        ReportDataDTO data = getReportSummary(filters);
        StringBuilder content = new StringBuilder();
        
        if ("csv".equalsIgnoreCase(format)) {
            // Add UTF-8 BOM for Excel compatibility
            content.append("\uFEFF");
            
            // Header Information
            content.append("DOCFLOW SYSTEM REPORT\n");
            content.append("Generated At,").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
            content.append("Time Range,").append(filters.getTimeRange()).append("\n");
            content.append("\n");
            
            // Summary Metrics
            content.append("SUMMARY METRICS\n");
            content.append("Metric,Value\n");
            content.append("Total Documents,").append(data.getTotalDocuments()).append("\n");
            content.append("Total Versions,").append(data.getTotalVersions()).append("\n");
            content.append("Pending Documents,").append(data.getPendingDocuments()).append("\n");
            content.append("Approved Documents,").append(data.getApprovedDocuments()).append("\n");
            content.append("Rejected Documents,").append(data.getRejectedDocuments()).append("\n");
            content.append("Average Processing Time (hours),").append(data.getAverageProcessingTime()).append("\n");
            content.append("\n");
            
            // Weekly Activity
            content.append("WEEKLY ACTIVITY\n");
            content.append("Day,Approved,Pending,Rejected\n");
            for (Map<String, Object> day : data.getWeeklyData()) {
                content.append(day.get("day")).append(",")
                       .append(day.get("approved")).append(",")
                       .append(day.get("pending")).append(",")
                       .append(day.get("rejected")).append("\n");
            }
            
            content.append("\n");
            // User Activity
            content.append("USER PERFORMANCE\n");
            content.append("User,Documents Processed,Percentage,Avg Processing Time\n");
            for (Map<String, Object> user : data.getUserActivity()) {
                content.append("\"").append(user.get("userName")).append("\",")
                       .append(user.get("documentsProcessed")).append(",")
                       .append(user.get("percentage")).append("%,")
                       .append(user.get("processingTime")).append("\n");
            }
        } else {
            // Basic text format for other types (already improved)
            content.append("DocFlow Report Summary\n");
            content.append("======================\n");
            content.append("Generated: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
            content.append("Time Range: ").append(filters.getTimeRange()).append("\n\n");
            content.append("Total Documents: ").append(data.getTotalDocuments()).append("\n");
            content.append("Total Versions: ").append(data.getTotalVersions()).append("\n");
            content.append("Approved: ").append(data.getApprovedDocuments()).append("\n");
            content.append("Pending: ").append(data.getPendingDocuments()).append("\n");
            content.append("Rejected: ").append(data.getRejectedDocuments()).append("\n");
            content.append("Avg Processing Time: ").append(data.getAverageProcessingTime()).append(" hours\n");
        }
        
        return content.toString();
    }
}
