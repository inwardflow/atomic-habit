package com.atomichabits.backend.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimiterService {

    private final Map<String, RequestWindow> requestCounts = new ConcurrentHashMap<>();
    private final Map<String, Instant> blockList = new ConcurrentHashMap<>();

    private static final int MAX_REQUESTS_PER_MINUTE = 10;
    private static final long BLOCK_DURATION_MS = 15 * 60 * 1000; // 15 minutes

    public boolean isBlocked(String ipAddress) {
        Instant blockEndTime = blockList.get(ipAddress);
        if (blockEndTime != null) {
            if (Instant.now().isBefore(blockEndTime)) {
                return true;
            } else {
                blockList.remove(ipAddress);
                return false;
            }
        }
        return false;
    }

    public void recordRequest(String ipAddress) {
        requestCounts.compute(ipAddress, (key, window) -> {
            long now = System.currentTimeMillis();
            if (window == null || now - window.startTime > 60000) {
                return new RequestWindow(now, 1);
            } else {
                window.count++;
                if (window.count > MAX_REQUESTS_PER_MINUTE) {
                    blockList.put(ipAddress, Instant.now().plusMillis(BLOCK_DURATION_MS));
                }
                return window;
            }
        });
    }

    @Scheduled(fixedRate = 60000)
    public void cleanup() {
        long now = System.currentTimeMillis();
        requestCounts.entrySet().removeIf(entry -> now - entry.getValue().startTime > 60000);
        blockList.entrySet().removeIf(entry -> Instant.now().isAfter(entry.getValue()));
    }

    private static class RequestWindow {
        long startTime;
        int count;

        RequestWindow(long startTime, int count) {
            this.startTime = startTime;
            this.count = count;
        }
    }
}
