package com.atomichabits.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdvancedUserStatsResponse {
    private List<DailyCompletionDTO> last30Days;
    private Map<String, Integer> completionsByHabit;
    private double overallCompletionRate;
    private List<MoodInsightDTO> moodInsights;
}
