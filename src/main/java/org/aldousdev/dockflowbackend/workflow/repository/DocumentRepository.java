package org.aldousdev.dockflowbackend.workflow.repository;

import org.aldousdev.dockflowbackend.workflow.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    @Query("SELECT d FROM Document d WHERE d.company.id = :companyId")
    List<Document> findByCompanyId(@Param("companyId") Long companyId);
}
