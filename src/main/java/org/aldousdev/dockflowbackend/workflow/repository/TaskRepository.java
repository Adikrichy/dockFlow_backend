package org.aldousdev.dockflowbackend.workflow.repository;

import org.aldousdev.dockflowbackend.workflow.entity.Document;
import org.aldousdev.dockflowbackend.workflow.entity.Task;
import org.aldousdev.dockflowbackend.workflow.entity.WorkflowInstance;
import org.aldousdev.dockflowbackend.workflow.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {
    @Query("SELECT t FROM Task t LEFT JOIN FETCH t.completedBy WHERE t.workflowInstance = :instance")
    List<Task> findByWorkflowInstance(@Param("instance") WorkflowInstance instance);

    @Query("SELECT t FROM Task t WHERE t.workflowInstance = :instance AND t.stepOrder = :stepOrder")
    Optional<Task> findByInstanceAndStep(@Param("instance") WorkflowInstance instance, 
                                          @Param("stepOrder") Integer stepOrder);

    @Query("SELECT t FROM Task t JOIN t.workflowInstance w WHERE w.document = :document")
    List<Task> findByWorkflowInstanceDocument(@Param("document") Document document);

    @Query("SELECT t FROM Task t WHERE t.status = :status AND t.requiredRoleName = :roleName")
    List<Task> findByStatusAndRequiredRoleName(@Param("status") TaskStatus status, 
                                               @Param("roleName") String roleName);
}
