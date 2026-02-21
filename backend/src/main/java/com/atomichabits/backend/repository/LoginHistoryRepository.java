package com.atomichabits.backend.repository;

import com.atomichabits.backend.model.LoginHistory;
import com.atomichabits.backend.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {
    Page<LoginHistory> findByUserIdOrderByLoginTimeDesc(Long userId, Pageable pageable);
    
    long countByUserAndStatusAndLoginTimeAfter(User user, String status, LocalDateTime loginTime);
}
