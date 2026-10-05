package com.atomichabits.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression test: {@code GET /api/users/stats/advanced} returned 500 once a user had completions,
 * because lazy {@code Habit} associations were read outside a transaction ({@code open-in-view: false}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AdvancedStatsIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void advancedStatsSucceedsForUserWithCompletionsAndMoods() throws Exception {
        String email = "stats-test+" + UUID.randomUUID() + "@example.com";
        String password = "StrongPass1!";

        // 1. Register
        request(HttpMethod.POST, "/api/auth/register", null, Map.of(
                "email", email,
                "password", password,
                "identityStatement", "I am a tester"
        ));

        // 2. Login
        ResponseEntity<String> loginResp = request(HttpMethod.POST, "/api/auth/login", null, Map.of("email", email, "password", password));
        String token = objectMapper.readTree(loginResp.getBody()).path("accessToken").asText();

        // 3. Create Goal
        ResponseEntity<String> goalResp = request(HttpMethod.POST, "/api/goals", token, Map.of(
                "name", "Test Goal",
                "startDate", LocalDate.now().toString(),
                "endDate", LocalDate.now().plusMonths(1).toString(),
                "status", "ACTIVE"
        ));
        long goalId = objectMapper.readTree(goalResp.getBody()).path("id").asLong();

        // 4. Create Habit
        ResponseEntity<String> habitResp = request(HttpMethod.POST, "/api/habits", token, Map.of(
                "name", "Test Habit",
                "goalId", goalId
        ));
        long habitId = objectMapper.readTree(habitResp.getBody()).path("id").asLong();

        // 5. Complete Habit
        request(HttpMethod.POST, "/api/habits/" + habitId + "/complete", token, null);

        // 6. Create Mood Log
        request(HttpMethod.POST, "/api/moods", token, Map.of(
                "moodType", "HAPPY",
                "note", "Feeling good"
        ));

        // 7. Call Advanced Stats
        ResponseEntity<String> statsResp = request(HttpMethod.GET, "/api/users/stats/advanced", token, null);

        assertEquals(HttpStatus.OK, statsResp.getStatusCode(), statsResp.getBody());
        JsonNode stats = objectMapper.readTree(statsResp.getBody());
        assertEquals(1, stats.path("completionsByHabit").path("Test Habit").asInt());
    }

    private ResponseEntity<String> request(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }

        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        return restTemplate.exchange("http://localhost:" + port + path, method, entity, String.class);
    }
}
