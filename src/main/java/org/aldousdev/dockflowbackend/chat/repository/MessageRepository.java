package org.aldousdev.dockflowbackend.chat.repository;

import org.aldousdev.dockflowbackend.chat.entity.ChatChannel;
import org.aldousdev.dockflowbackend.chat.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    @Query("SELECT m FROM Message m LEFT JOIN FETCH m.sender WHERE m.channel = :channel ORDER BY m.createdAt ASC")
    List<Message> findByChannel(@Param("channel") ChatChannel channel);

    @Query("SELECT m FROM Message m LEFT JOIN FETCH m.sender WHERE m.channel = :channel ORDER BY m.createdAt ASC")
    Page<Message> findByChannelPaginated(@Param("channel") ChatChannel channel, Pageable pageable);
}
