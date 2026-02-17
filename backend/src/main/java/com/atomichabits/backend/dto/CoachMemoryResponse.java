package com.atomichabits.backend.dto;

import com.atomichabits.backend.model.CoachMemory.MemoryType;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class CoachMemoryResponse {
    private Long id;
    private MemoryType type;
    private String content;
    private LocalDate referenceDate;
    private LocalDateTime createdAt;
    private Integer importanceScore;
    private LocalDate expiresAt;
    private String formattedDate; // For easier frontend display (e.g. "Oct 24")
}
