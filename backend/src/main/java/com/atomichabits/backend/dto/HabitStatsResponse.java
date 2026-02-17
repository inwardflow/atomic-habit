package com.atomichabits.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HabitStatsResponse {
    private Long habitId;
    private int currentStreak;
    private int longestStreak;
    private int totalCompletions;
    private double completionRate;
}
