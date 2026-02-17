package com.atomichabits.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStatsResponse {
    private int identityScore;
    private int currentStreak;
    private int longestStreak;
    private int totalHabitsCompleted;
    private List<BadgeResponse> badges;
}
