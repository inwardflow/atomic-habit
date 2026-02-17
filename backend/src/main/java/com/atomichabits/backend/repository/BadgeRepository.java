package com.atomichabits.backend.repository;

import com.atomichabits.backend.model.Badge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BadgeRepository extends JpaRepository<Badge, Long> {
    List<Badge> findByUserId(Long userId);
    boolean existsByUserIdAndName(Long userId, String name);
}
