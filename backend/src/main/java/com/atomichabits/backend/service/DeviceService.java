package com.atomichabits.backend.service;

import com.atomichabits.backend.dto.DeviceMetadata;
import org.springframework.stereotype.Service;

@Service
public class DeviceService {

    public DeviceMetadata parseUserAgent(String userAgent) {
        if (userAgent == null) {
            return DeviceMetadata.builder()
                    .browser("Unknown")
                    .operatingSystem("Unknown")
                    .deviceType("Unknown")
                    .build();
        }

        String browser = "Unknown";
        String os = "Unknown";
        String deviceType = "Desktop"; // Default

        // Simple OS detection
        if (userAgent.contains("Windows")) os = "Windows";
        else if (userAgent.contains("Mac OS")) os = "macOS";
        else if (userAgent.contains("Linux")) os = "Linux";
        else if (userAgent.contains("Android")) { os = "Android"; deviceType = "Mobile"; }
        else if (userAgent.contains("iPhone") || userAgent.contains("iPad")) { os = "iOS"; deviceType = "Mobile"; }

        // Simple Browser detection
        if (userAgent.contains("Edg")) browser = "Edge";
        else if (userAgent.contains("Chrome") && !userAgent.contains("Edg")) browser = "Chrome";
        else if (userAgent.contains("Firefox")) browser = "Firefox";
        else if (userAgent.contains("Safari") && !userAgent.contains("Chrome")) browser = "Safari";
        else if (userAgent.contains("Trident")) browser = "Internet Explorer";

        return DeviceMetadata.builder()
                .browser(browser)
                .operatingSystem(os)
                .deviceType(deviceType)
                .build();
    }

    public String getLocationFromIp(String ipAddress) {
        // Placeholder for GeoIP service
        if (ipAddress.equals("127.0.0.1") || ipAddress.equals("0:0:0:0:0:0:0:1")) {
            return "Localhost";
        }
        return "Unknown Location";
    }
}
