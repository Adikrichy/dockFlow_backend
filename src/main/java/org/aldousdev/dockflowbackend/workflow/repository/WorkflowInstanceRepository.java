package org.aldousdev.dockflowbackend.workflow.repository;

import org.aldousdev.dockflowbackend.workflow.entity.WorkflowInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, Long> {
    @Query("SELECT wi FROM WorkflowInstance wi LEFT JOIN FETCH wi.document WHERE wi.id = :id")
    Optional<WorkflowInstance> findByIdWithDocument(@Param("id") Long id);

    List<WorkflowInstance> findByDocumentId(Long documentId);
}
