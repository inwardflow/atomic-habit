package com.atomichabits.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "weekly_reviews")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeeklyReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Integer totalCompleted;

    @Column(nullable = false)
    private Integer currentStreak;

    private Integer bestStreak;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String suggestion;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String highlightsJson;

    @Column(columnDefinition = "TEXT")
    private String rawResponse;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
