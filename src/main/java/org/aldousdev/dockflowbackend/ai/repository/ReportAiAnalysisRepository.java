package org.aldousdev.dockflowbackend.ai.repository;

import org.aldousdev.dockflowbackend.ai.entity.ReportAiAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ReportAiAnalysisRepository extends JpaRepository<ReportAiAnalysis, Long> {
    Optional<ReportAiAnalysis> findByCompanyIdAndTimeRange(Long companyId, String timeRange);
    Optional<ReportAiAnalysis> findByCorrelationId(String correlationId);
    Optional<ReportAiAnalysis> findFirstByCompanyIdAndTimeRangeAndUpdatedAtAfterOrderByUpdatedAtDesc(Long companyId, String timeRange, LocalDateTime after);
}
