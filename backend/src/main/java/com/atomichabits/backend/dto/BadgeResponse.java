package com.atomichabits.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BadgeResponse {
    private Long id;
    private String name;
    private String description;
    private String icon;
    private LocalDateTime earnedAt;
}
