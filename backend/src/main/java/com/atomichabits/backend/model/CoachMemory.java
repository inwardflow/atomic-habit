package com.atomichabits.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder.Default;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "coach_memories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoachMemory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemoryType type;

    // The actual summary or insight content
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    // The date this memory refers to (e.g., summary of 2023-10-27)
    private LocalDate referenceDate;

    @Default
    @Column(nullable = false)
    private Integer importanceScore = 3;

    // Null means this memory does not expire.
    private LocalDate expiresAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum MemoryType {
        DAILY_SUMMARY,
        USER_INSIGHT,
        LONG_TERM_FACT
    }
}
