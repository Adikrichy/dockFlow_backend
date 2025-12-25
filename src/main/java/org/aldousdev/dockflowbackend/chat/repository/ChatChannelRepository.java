package org.aldousdev.dockflowbackend.chat.repository;

import org.aldousdev.dockflowbackend.auth.entity.Company;
import org.aldousdev.dockflowbackend.chat.entity.ChatChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatChannelRepository extends JpaRepository<ChatChannel, Long> {
    List<ChatChannel> findByCompany(Company company);
    Optional<ChatChannel> findByIdAndCompany(Long id, Company company);
    List<ChatChannel> findByCompanyAndIsPublicTrue(Company company);
}
