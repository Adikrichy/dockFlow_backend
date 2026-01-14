package org.aldousdev.dockflowbackend.reports.repository;

import org.aldousdev.dockflowbackend.reports.entity.SavedReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SavedReportRepository extends JpaRepository<SavedReport, Long> {
    List<SavedReport> findByCompanyId(Long companyId);
}
