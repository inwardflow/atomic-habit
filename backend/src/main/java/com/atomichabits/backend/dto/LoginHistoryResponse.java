package com.atomichabits.backend.dto;

import com.atomichabits.backend.model.LoginHistory;

import java.time.LocalDateTime;

/** Login history entry exposed to the client; never includes the owning {@code User} entity. */
public record LoginHistoryResponse(
        Long id,
        String ipAddress,
        String deviceInfo,
        String browser,
        String operatingSystem,
        String deviceType,
        String location,
        String status,
        LocalDateTime loginTime) {

    public static LoginHistoryResponse from(LoginHistory h) {
        return new LoginHistoryResponse(h.getId(), h.getIpAddress(), h.getDeviceInfo(), h.getBrowser(),
                h.getOperatingSystem(), h.getDeviceType(), h.getLocation(), h.getStatus(), h.getLoginTime());
    }
}
