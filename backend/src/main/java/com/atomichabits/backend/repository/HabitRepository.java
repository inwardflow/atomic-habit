package com.atomichabits.backend.repository;

import com.atomichabits.backend.model.Habit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HabitRepository extends JpaRepository<Habit, Long> {
    List<Habit> findByUserIdAndIsActiveTrue(Long userId);
    List<Habit> findByUserId(Long userId);
}
