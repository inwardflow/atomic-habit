package com.atomichabits.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class HabitRequest {
    @NotBlank(message = "Name is mandatory")
    private String name;
    
    private String twoMinuteVersion;
    private String cueImplementationIntention;
    private String cueHabitStack;
    private Long goalId;
}
