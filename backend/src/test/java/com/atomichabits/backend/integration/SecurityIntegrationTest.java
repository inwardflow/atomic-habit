package com.atomichabits.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "coach.memory.llm-extraction-enabled=false"
        }
)
@ActiveProfiles("test")
@TestPropertySource(properties = "app.rate-limit.max-requests=10")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SecurityIntegrationTest {

    @LocalServerPort
    private int port;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();

    @Test
    void testRateLimitingOnLogin() throws Exception {
        // Use a unique email prefix
        String emailPrefix = "rate-limit-" + UUID.randomUUID();

        // Rate limit is 10 per minute per IP
        // We need to exceed 10 requests.
        // To avoid Account Locking (which triggers after 5 failed attempts per USER),
        // we should use different emails or valid logins?
        // Valid logins also count towards rate limit.
        // But creating 10 users is slow.
        // We can use non-existent users. Account locking usually requires an existing user to lock?
        // Let's check AuthService.
        // Optional<User> userOpt = userRepository.findByEmail(loginRequest.getEmail());
        // if (userOpt.isPresent()) { ... check lock ... }
        // So if user doesn't exist, Account Locking logic is skipped!
        // Perfect. We can use random emails.

        for (int i = 0; i < 12; i++) {
            String randomEmail = emailPrefix + i + "@example.com";
            HttpResponse<String> resp = sendPostRequest(
                    "/api/auth/login",
                    Map.of("email", randomEmail, "password", "Whatever1!")
            );

            if (resp.statusCode() == 429) {
                return; // Pass if we hit the limit
            }
        }

        // If we finished the loop without 429, we failed
        // But wait, maybe the limit is higher?
        // RateLimiterService says 10.
        // Let's try one more.
        HttpResponse<String> finalResp = sendPostRequest(
                "/api/auth/login",
                Map.of("email", emailPrefix + "final@example.com", "password", "Whatever1!")
        );
        assertEquals(429, finalResp.statusCode(), "Should be Rate Limited (429)");
    }

    @Test
    void testAccountLocking() throws Exception {
        String email = "lock-test+" + UUID.randomUUID() + "@example.com";
        String password = "StrongPass1!";

        // Register
        sendPostRequest(
                "/api/auth/register",
                Map.of(
                        "email", email,
                        "password", password
                )
        );

        // 5 failed attempts
        for (int i = 0; i < 5; i++) {
            sendPostRequest(
                    "/api/auth/login",
                    Map.of("email", email, "password", "WrongPass1!")
            );
        }

        // 6th attempt with CORRECT password should fail because account is locked
        HttpResponse<String> lockedResp = sendPostRequest(
                "/api/auth/login",
                Map.of("email", email, "password", password)
        );

        assertEquals(403, lockedResp.statusCode(), "Account should be locked (403)");
        // The message might be localized, so we strictly check the status code which is the API contract.
        // We can optionally check if body contains "locked" or "锁定"
        // assertTrue(lockedResp.body().contains("locked") || lockedResp.body().contains("锁定"));
    }

    private HttpResponse<String> sendPostRequest(String path, Object body) throws Exception {
        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url(path)))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE);

        java.net.http.HttpRequest.BodyPublisher bodyPublisher;
        if (body == null) {
            bodyPublisher = java.net.http.HttpRequest.BodyPublishers.noBody();
        } else {
            bodyPublisher = java.net.http.HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body));
        }

        builder.POST(bodyPublisher);

        return httpClient.send(builder.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
