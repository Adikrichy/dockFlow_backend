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
import org.aldousdev.dockflowbackend.ai.repository.ReportAiAnalysisRepository;
import org.aldousdev.dockflowbackend.ai.producer.AiTaskProducer;
import org.aldousdev.dockflowbackend.ai.entity.ReportAiAnalysis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ReportAiAnalysisRepository reportAiAnalysisRepository;
    private final AiTaskProducer aiTaskProducer;

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
        User user = userRepository.findById(userId).orElse(null);
        if (user != null && user.isAiAssistant()) return true;
        
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

    public ReportDataDTO getReportSummary(ReportFiltersDTO filters, Long userId) {
        log.info("Generating report summary with filters: {}, userId: {}", filters, userId);

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

        // Apply Personal Filter If Provided
        if (userId != null) {
            documents = documents.stream()
                .filter(d -> d.getUploadedBy() != null && d.getUploadedBy().getId().equals(userId))
                .collect(Collectors.toList());
            allTasksInRange = allTasksInRange.stream()
                .filter(t -> t.getCompletedBy() != null && t.getCompletedBy().getId().equals(userId))
                .collect(Collectors.toList());
            completedTasksInRange = completedTasksInRange.stream()
                .filter(t -> t.getCompletedBy() != null && t.getCompletedBy().getId().equals(userId))
                .collect(Collectors.toList());
        }

        long activeUsersCount = userId != null ? 1 : securityAuditRepository.countDistinctByTimestampAfter(startDate);
        
        long totalVersions;
        if (companyId != null) {
            totalVersions = documentVersionRepository.countByCompanyIdAndCreatedAtBetween(companyId, startDate, endDate);
        } else {
            totalVersions = documentVersionRepository.countByCreatedAtBetween(startDate, endDate);
        }
        
        // If personal mode, we need to manually count versions too for accuracy
        if (userId != null) {
             totalVersions = documents.stream()
                .mapToLong(d -> documentVersionRepository.countByDocumentId(d.getId()))
                .sum();
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
        reportData.setTotalUsers(userId != null ? 1 : userRepository.count());
        reportData.setActiveUsers(activeUsersCount);

        // Weekly data (passing userId)
        reportData.setWeeklyData(getWeeklyData(filters, userId));
        
        // User activity (for personal mode, this is just the user themselves)
        reportData.setUserActivity(getUserActivityInternal(filters, userId));
        
        // Document types (passing userId filter implicitly via the internal method update)
        reportData.setDocumentTypes(getDocumentTypesInternal(filters, userId));

        return reportData;
    }

    public List<Map<String, Object>> getWeeklyActivity(ReportFiltersDTO filters, Long userId) {
        return getWeeklyData(filters, userId);
    }
 
    public List<Map<String, Object>> getWeeklyActivity(String timeRange, Long userId) {
        ReportFiltersDTO filters = new ReportFiltersDTO();
        filters.setTimeRange(timeRange);
        return getWeeklyData(filters, userId);
    }
    
    private List<Map<String, Object>> getWeeklyData(ReportFiltersDTO filters, Long userId) {
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

        // Apply Personal Filter
        if (userId != null) {
            uploadedDocuments = uploadedDocuments.stream()
                .filter(d -> d.getUploadedBy() != null && d.getUploadedBy().getId().equals(userId))
                .collect(Collectors.toList());
            completedTasks = completedTasks.stream()
                .filter(t -> t.getCompletedBy() != null && t.getCompletedBy().getId().equals(userId))
                .collect(Collectors.toList());
        }

        // Clip empty period at the start if "All Time"
        if ("alltime".equalsIgnoreCase(filters.getTimeRange()) && !uploadedDocuments.isEmpty()) {
            LocalDateTime earliestDoc = uploadedDocuments.stream()
                .map(Document::getUploadedAt)
                .min(LocalDateTime::compareTo)
                .get();
            if (earliestDoc.isAfter(startDate)) {
                startDate = earliestDoc.withDayOfMonth(1).with(LocalTime.MIN);
            }
        }

        long daysDiff = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        List<Map<String, Object>> weeklyData = new ArrayList<>();
        
        if (daysDiff > 45) {
            // Group by Year-Month
            Map<String, List<Document>> docsByMonth = uploadedDocuments.stream()
                .collect(Collectors.groupingBy(d -> d.getUploadedAt().format(DateTimeFormatter.ofPattern("yyyy-MM"))));
            Map<String, List<Task>> tasksByMonth = completedTasks.stream()
                .collect(Collectors.groupingBy(t -> t.getCompletedAt().format(DateTimeFormatter.ofPattern("yyyy-MM"))));
            
            LocalDateTime current = startDate.withDayOfMonth(1).with(LocalTime.MIN);
            boolean dataFound = false;
            
            while (!current.isAfter(endDate)) {
                String label = current.format(DateTimeFormatter.ofPattern("yyyy-MM"));
                String displayLabel = current.format(DateTimeFormatter.ofPattern("MMM yy"));
                
                List<Document> monthDocs = docsByMonth.getOrDefault(label, Collections.emptyList());
                List<Task> monthTasks = tasksByMonth.getOrDefault(label, Collections.emptyList());
                
                long approved = monthTasks.stream().filter(t -> org.aldousdev.dockflowbackend.workflow.enums.TaskStatus.APPROVED.equals(t.getStatus())).count();
                long rejected = monthTasks.stream().filter(t -> org.aldousdev.dockflowbackend.workflow.enums.TaskStatus.REJECTED.equals(t.getStatus())).count();
                long pending = monthDocs.size();

                // Skip leading empty months for All Time
                if (!dataFound && "alltime".equalsIgnoreCase(filters.getTimeRange()) && pending == 0 && approved == 0 && rejected == 0) {
                    current = current.plusMonths(1);
                    continue;
                }
                dataFound = true;
                
                Map<String, Object> point = new HashMap<>();
                point.put("day", displayLabel);
                point.put("approved", approved);
                point.put("rejected", rejected);
                point.put("pending", pending);
                
                weeklyData.add(point);
                current = current.plusMonths(1);
            }
        } else {
            // Group documents by date
            Map<LocalDate, List<Document>> docsByDate = uploadedDocuments.stream()
                .collect(Collectors.groupingBy(d -> d.getUploadedAt().toLocalDate()));
            Map<LocalDate, List<Task>> tasksByDate = completedTasks.stream()
                .collect(Collectors.groupingBy(t -> t.getCompletedAt().toLocalDate()));
                
            for (int i = 0; i < daysDiff; i++) {
                LocalDate date = startDate.plusDays(i).toLocalDate();
                if (date.isAfter(endDate.toLocalDate())) break;
                
                List<Document> dayDocs = docsByDate.getOrDefault(date, Collections.emptyList());
                List<Task> dayTasks = tasksByDate.getOrDefault(date, Collections.emptyList());
                
                Map<String, Object> dayData = new HashMap<>();
                String dayLabel = daysDiff <= 14 ? date.format(DateTimeFormatter.ofPattern("EEE")) : date.format(DateTimeFormatter.ofPattern("dd MMM")); 
                
                dayData.put("day", dayLabel);
                dayData.put("approved", dayTasks.stream().filter(t -> org.aldousdev.dockflowbackend.workflow.enums.TaskStatus.APPROVED.equals(t.getStatus())).count());
                dayData.put("rejected", dayTasks.stream().filter(t -> org.aldousdev.dockflowbackend.workflow.enums.TaskStatus.REJECTED.equals(t.getStatus())).count());
                dayData.put("pending", dayDocs.size());
                
                weeklyData.add(dayData);
            }
        }
        
        return weeklyData;
    }


    public List<Map<String, Object>> getUserActivity(ReportFiltersDTO filters, Long userId) {
        return getUserActivityInternal(filters, userId);
    }
 
    public List<Map<String, Object>> getUserActivity(String timeRange, Long userId) {
        ReportFiltersDTO filters = new ReportFiltersDTO();
        filters.setTimeRange(timeRange);
        return getUserActivityInternal(filters, userId);
    }

    private List<Map<String, Object>> getUserActivityInternal(ReportFiltersDTO filters, Long userId) {
        LocalDateTime startDate = getStartDate(filters.getTimeRange());
        LocalDateTime endDate = LocalDateTime.now();
        Long companyId = parseCompanyId(filters.getCompany());

        List<Task> completedTasks = taskRepository.findByCompletedAtBetween(startDate, endDate);
        if (companyId != null) {
            completedTasks = completedTasks.stream()
                .filter(t -> t.getWorkflowInstance().getDocument().getCompany().getId().equals(companyId))
                .collect(Collectors.toList());
        }
        
        // Apply Personal Filter
        if (userId != null) {
            completedTasks = completedTasks.stream()
                .filter(t -> t.getCompletedBy() != null && t.getCompletedBy().getId().equals(userId))
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

    public List<Map<String, Object>> getDocumentTypes(ReportFiltersDTO filters, Long userId) {
        return getDocumentTypesInternal(filters, userId);
    }
 
    public List<Map<String, Object>> getDocumentTypes(String timeRange, Long userId) {
        ReportFiltersDTO filters = new ReportFiltersDTO();
        filters.setTimeRange(timeRange);
        return getDocumentTypesInternal(filters, userId);
    }
    
    private List<Map<String, Object>> getDocumentTypesInternal(ReportFiltersDTO filters, Long userId) {
        LocalDateTime startDate = getStartDate(filters.getTimeRange());
        LocalDateTime endDate = LocalDateTime.now();
        Long companyId = parseCompanyId(filters.getCompany());

        List<Document> documents = documentRepository.findByUploadedAtBetween(startDate, endDate);
        if (companyId != null) {
            documents = documents.stream()
                .filter(d -> d.getCompany().getId().equals(companyId))
                .collect(Collectors.toList());
        }

        // Apply Personal Filter
        if (userId != null) {
            documents = documents.stream()
                .filter(d -> d.getUploadedBy() != null && d.getUploadedBy().getId().equals(userId))
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

    public ByteArrayResource exportReport(ReportFiltersDTO filters, String format, Long userId) {
        String content = generateReportContent(filters, format, userId);
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

    public LocalDateTime getStartDate(String timeRange) {
        LocalDateTime now = LocalDateTime.now();
        if (timeRange == null) return now.minus(7, ChronoUnit.DAYS);
        
        return switch (timeRange.toLowerCase()) {
            case "lastweek" -> now.minus(14, ChronoUnit.DAYS);
            case "thismonth" -> now.withDayOfMonth(1).with(LocalTime.MIN);
            case "thisyear" -> now.withDayOfYear(1).with(LocalTime.MIN);
            case "alltime" -> LocalDateTime.of(2020, 1, 1, 0, 0); // Start of system life
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

    private String generateReportContent(ReportFiltersDTO filters, String format, Long userId) {
        ReportDataDTO data = getReportSummary(filters, userId);
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

    @Transactional
    public ReportAiAnalysis getAiInsights(Long companyId, String timeRange) {
        // Look for existing analysis in the last 12 hours
        LocalDateTime twelveHoursAgo = LocalDateTime.now().minusHours(12);
        Optional<ReportAiAnalysis> existing = reportAiAnalysisRepository
                .findFirstByCompanyIdAndTimeRangeAndUpdatedAtAfterOrderByUpdatedAtDesc(companyId, timeRange, twelveHoursAgo);
        
        if (existing.isPresent()) {
            ReportAiAnalysis analysis = existing.get();
            if ("SUCCESS".equals(analysis.getStatus())) {
                return analysis;
            } else if ("PENDING".equals(analysis.getStatus()) && analysis.getUpdatedAt().isAfter(LocalDateTime.now().minusMinutes(5))) {
                return analysis; // return pending if it's recent (still processing)
            }
            // If it's old PENDING or ERROR, we proceed to create a new one
        }
        
        // Otherwise, trigger new analysis
        ReportFiltersDTO filters = new ReportFiltersDTO();
        filters.setTimeRange(timeRange);
        filters.setCompany(companyId.toString());
        ReportDataDTO currentData = getReportSummary(filters, null);
        
        String correlationId = "report-" + companyId + "-" + timeRange + "-" + System.currentTimeMillis();
        
        ReportAiAnalysis analysis = ReportAiAnalysis.builder()
                .companyId(companyId)
                .timeRange(timeRange)
                .status("PENDING")
                .correlationId(correlationId)
                .build();
        
        analysis = reportAiAnalysisRepository.save(analysis);
        
        aiTaskProducer.sendReportInsights(currentData, companyId, timeRange, correlationId);
        
        return analysis;
    }
}
