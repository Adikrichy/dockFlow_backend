package org.aldousdev.dockflowbackend.workflow.repository;

import org.aldousdev.dockflowbackend.workflow.entity.Task;
import org.aldousdev.dockflowbackend.workflow.entity.WorkflowAuditLog;
import org.aldousdev.dockflowbackend.workflow.entity.WorkflowInstance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface WorkflowAuditLogRepository extends JpaRepository<WorkflowAuditLog, Long> {
    
    List<WorkflowAuditLog> findByWorkflowInstance(WorkflowInstance instance);

    List<WorkflowAuditLog> findByTask(Task task);

    @Query("SELECT log FROM WorkflowAuditLog log WHERE log.workflowInstance = :instance ORDER BY log.createdAt DESC")
    List<WorkflowAuditLog> findByWorkflowInstanceOrderedByTime(@Param("instance") WorkflowInstance instance);

    @Query("SELECT log FROM WorkflowAuditLog log WHERE log.actionType IN :actionTypes ORDER BY log.createdAt DESC")
    Page<WorkflowAuditLog> findByActionTypes(@Param("actionTypes") List<String> actionTypes, Pageable pageable);

    @Query("SELECT log FROM WorkflowAuditLog log WHERE log.createdAt BETWEEN :start AND :end ORDER BY log.createdAt DESC")
    List<WorkflowAuditLog> findByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(log) FROM WorkflowAuditLog log WHERE log.actionType = :actionType AND log.workflowInstance = :instance")
    Long countActionsByType(@Param("actionType") String actionType, @Param("instance") WorkflowInstance instance);
}
