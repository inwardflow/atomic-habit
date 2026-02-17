package com.atomichabits.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class HabitRequest {
    @NotBlank(message = "Name is mandatory")
    private String name;
    
    private String twoMinuteVersion;
    private String cueImplementationIntention;
    private String cueHabitStack;
    private Long goalId;

    // List of day names: ["MONDAY", "TUESDAY", ...], null/empty = daily
    private List<String> frequency;
}
