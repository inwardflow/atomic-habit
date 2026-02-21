package com.atomichabits.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterServiceTest {

    @Test
    void testRateLimiting() {
        RateLimiterService service = new RateLimiterService();
        ReflectionTestUtils.setField(service, "maxRequestsPerMinute", 10);
        ReflectionTestUtils.setField(service, "blockDurationMs", 900000L);
        String ip = "127.0.0.1";

        // Record 10 requests (allowed)
        for (int i = 0; i < 10; i++) {
            assertFalse(service.isBlocked(ip));
            service.recordRequest(ip);
        }

        // 11th request should trigger block
        assertFalse(service.isBlocked(ip)); // Still not blocked before recording
        service.recordRequest(ip);
        
        // Now it should be blocked
        assertTrue(service.isBlocked(ip));
    }
}
