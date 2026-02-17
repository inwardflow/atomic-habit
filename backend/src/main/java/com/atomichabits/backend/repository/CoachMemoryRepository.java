package com.atomichabits.backend.repository;

import com.atomichabits.backend.model.CoachMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CoachMemoryRepository extends JpaRepository<CoachMemory, Long> {
    List<CoachMemory> findByUserIdOrderByReferenceDateDesc(Long userId);
    
    Optional<CoachMemory> findByUserIdAndReferenceDateAndType(Long userId, LocalDate referenceDate, CoachMemory.MemoryType type);
    
    // Get last N summaries
    List<CoachMemory> findTop10ByUserIdAndTypeOrderByReferenceDateDesc(Long userId, CoachMemory.MemoryType type);
    
    // Get last 30 memories (all types)
    List<CoachMemory> findTop30ByUserIdOrderByCreatedAtDesc(Long userId);

    // Get last N memories by type (latest first)
    List<CoachMemory> findTop20ByUserIdAndTypeOrderByCreatedAtDesc(Long userId, CoachMemory.MemoryType type);

    List<CoachMemory> findTop10ByUserIdAndTypeOrderByCreatedAtDesc(Long userId, CoachMemory.MemoryType type);
}
