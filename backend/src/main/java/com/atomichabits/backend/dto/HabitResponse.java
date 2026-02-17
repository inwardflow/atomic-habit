package com.atomichabits.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

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
    private int currentStreak;
    private LocalDateTime createdAt;
}
