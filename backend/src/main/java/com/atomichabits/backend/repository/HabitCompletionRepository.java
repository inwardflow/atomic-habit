package com.atomichabits.backend.repository;

import com.atomichabits.backend.model.HabitCompletion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface HabitCompletionRepository extends JpaRepository<HabitCompletion, Long> {
    List<HabitCompletion> findByHabitId(Long habitId);
    List<HabitCompletion> findByHabitUserId(Long userId);
    boolean existsByHabitIdAndCompletedAtBetween(Long habitId, LocalDateTime start, LocalDateTime end);
    void deleteByHabitIdAndCompletedAtBetween(Long habitId, LocalDateTime start, LocalDateTime end);
    java.util.Optional<HabitCompletion> findFirstByHabitIdOrderByCompletedAtDesc(Long habitId);
    List<HabitCompletion> findByHabitIdOrderByCompletedAtDesc(Long habitId);
    List<HabitCompletion> findByHabitUserIdAndCompletedAtBetween(Long userId, LocalDateTime start, LocalDateTime end);
}
