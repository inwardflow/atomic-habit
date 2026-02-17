package com.atomichabits.backend.repository;

import com.atomichabits.backend.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.LocalDateTime;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByUserIdOrderByTimestampAsc(Long userId);
    List<ChatMessage> findByUserIdOrderByTimestampDesc(Long userId, Pageable pageable);
    List<ChatMessage> findByUserIdAndTimestampBetweenOrderByTimestampAsc(Long userId, LocalDateTime start, LocalDateTime end);
}
