package com.atomichabits.backend.repository;

import com.atomichabits.backend.model.WeeklyReview;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WeeklyReviewRepository extends JpaRepository<WeeklyReview, Long> {
    List<WeeklyReview> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
