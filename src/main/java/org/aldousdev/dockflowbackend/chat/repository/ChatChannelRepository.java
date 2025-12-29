package org.aldousdev.dockflowbackend.chat.repository;

import org.aldousdev.dockflowbackend.auth.entity.Company;
import org.aldousdev.dockflowbackend.auth.entity.User;
import org.aldousdev.dockflowbackend.chat.entity.ChatChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Optional;

public interface ChatChannelRepository extends JpaRepository<ChatChannel, Long> {
    List<ChatChannel> findByCompany(Company company);
    Optional<ChatChannel> findByIdAndCompany(Long id, Company company);
    List<ChatChannel> findByCompanyAndTypeAndIsPublicTrue(Company company, ChatChannel.ChannelType type);
    
    @Query("SELECT c FROM ChatChannel c JOIN c.members m1 JOIN c.members m2 WHERE c.type = 'DM' AND m1 = :user1 AND m2 = :user2")
    Optional<ChatChannel> findDMChannel(@Param("user1") User user1, @Param("user2") User user2);

    @Query("SELECT c FROM ChatChannel c JOIN c.members m WHERE c.type = 'DM' AND m = :user")
    List<ChatChannel> findUserDMs(@Param("user") User user);
}
