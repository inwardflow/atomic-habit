package com.atomichabits.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "mood_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MoodLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String moodType; // e.g., "OVERWHELMED", "MOTIVATED", "GRATITUDE"

    private String note;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
