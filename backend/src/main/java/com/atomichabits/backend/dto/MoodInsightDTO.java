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
public class MoodInsightDTO {
    private String mood;
    private int logCount;
    private double avgCompletions; // Average number of habits completed on days with this mood
    private List<String> topHabits; // Names of habits frequently completed
}
