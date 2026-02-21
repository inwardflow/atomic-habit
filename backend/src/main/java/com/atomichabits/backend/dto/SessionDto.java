package com.atomichabits.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionDto {
    private Long id;
    private String ipAddress;
    private String deviceInfo;
    private String browser;
    private String operatingSystem;
    private String deviceType;
    private String location;
    private LocalDateTime lastActive;
    private boolean isCurrent;
}
