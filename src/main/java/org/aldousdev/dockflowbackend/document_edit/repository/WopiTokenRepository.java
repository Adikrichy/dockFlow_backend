package org.aldousdev.dockflowbackend.document_edit.repository;

import org.aldousdev.dockflowbackend.document_edit.entity.WopiToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WopiTokenRepository extends JpaRepository<WopiToken, String> {
    Optional<WopiToken> findByToken(String token);
}
