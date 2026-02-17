package com.atomichabits.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class WeeklyReviewResponse {
    private Long id;
    private Integer totalCompleted;
    private Integer currentStreak;
    private Integer bestStreak;
    private List<String> highlights;
    private String suggestion;
    private LocalDateTime createdAt;
    private String formattedDate;
}
