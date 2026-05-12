package org.aldousdev.dockflowbackend.auth.repository;

import org.aldousdev.dockflowbackend.auth.entity.CompanyInviteToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyInviteTokenRepository extends JpaRepository<CompanyInviteToken, Long> {
    Optional<CompanyInviteToken> findByToken(String token);
    void deleteByInvitedEmail(String invitedEmail);
}
