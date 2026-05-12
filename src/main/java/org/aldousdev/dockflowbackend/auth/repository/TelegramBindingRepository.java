package org.aldousdev.dockflowbackend.auth.repository;

import org.aldousdev.dockflowbackend.auth.entity.TelegramBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TelegramBindingRepository extends JpaRepository<TelegramBinding, Long> {
    Optional<TelegramBinding> findByLinkingToken(String linkingToken);
    Optional<TelegramBinding> findByTelegramId(Long telegramId);
    Optional<TelegramBinding> findByUserId(Long userId);
    
    void deleteByUserId(Long userId);
}
