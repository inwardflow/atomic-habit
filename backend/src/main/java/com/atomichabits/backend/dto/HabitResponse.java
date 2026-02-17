package com.atomichabits.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class HabitResponse {
    private Long id;
    private String name;
    private String twoMinuteVersion;
    private String cueImplementationIntention;
    private String cueHabitStack;
    @JsonProperty("isActive")
    private boolean isActive;
    private boolean completedToday;
    private boolean scheduledToday;
    private int currentStreak;
    // List of day names, null/empty = daily
    private List<String> frequency;
    private LocalDateTime createdAt;
}
