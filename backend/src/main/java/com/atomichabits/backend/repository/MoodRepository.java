package com.atomichabits.backend.repository;

import com.atomichabits.backend.model.MoodLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface MoodRepository extends JpaRepository<MoodLog, Long> {
    List<MoodLog> findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(Long userId, LocalDateTime timestamp);
    List<MoodLog> findByUserIdAndCreatedAtBetween(Long userId, LocalDateTime start, LocalDateTime end);
    List<MoodLog> findByUserIdAndMoodTypeOrderByCreatedAtDesc(Long userId, String moodType);
    Optional<MoodLog> findTopByUserIdAndMoodTypeAndCreatedAtBetweenOrderByCreatedAtDesc(Long userId, String moodType, LocalDateTime start, LocalDateTime end);
    Page<MoodLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
