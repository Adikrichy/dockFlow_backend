package org.aldousdev.dockflowbackend.workflow.repository;

import org.aldousdev.dockflowbackend.workflow.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {
}
