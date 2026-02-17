package com.atomichabits.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "agentscope.enabled=false",
                "coach.memory.llm-extraction-enabled=false"
        }
)
@ActiveProfiles("test")
class ApiFlowIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldCoverCoreUserFlowsAcrossAllControllers() throws Exception {
        String email = "e2e+" + UUID.randomUUID() + "@example.com";
        String password = "password123";

        ResponseEntity<String> registerResp = request(
                HttpMethod.POST,
                "/api/auth/register",
                null,
                Map.of(
                        "email", email,
                        "password", password,
                        "identityStatement", "I am becoming consistent"
                )
        );
        assertTrue(registerResp.getStatusCode().is2xxSuccessful());

        ResponseEntity<String> duplicateRegisterResp = request(
                HttpMethod.POST,
                "/api/auth/register",
                null,
                Map.of(
                        "email", email,
                        "password", password
                )
        );
        assertTrue(duplicateRegisterResp.getStatusCode().is4xxClientError());

        ResponseEntity<String> loginResp = request(
                HttpMethod.POST,
                "/api/auth/login",
                null,
                Map.of("email", email, "password", password)
        );
        assertTrue(loginResp.getStatusCode().is2xxSuccessful());
        String token = json(loginResp).path("accessToken").asText();
        assertFalse(token.isBlank());

        ResponseEntity<String> unauthorizedProfile = request(HttpMethod.GET, "/api/users/me", null, null);
        assertTrue(unauthorizedProfile.getStatusCode().is4xxClientError());

        ResponseEntity<String> meResp = request(HttpMethod.GET, "/api/users/me", token, null);
        assertTrue(meResp.getStatusCode().is2xxSuccessful());
        assertTrue(meResp.getBody().contains(email));

        ResponseEntity<String> updateMeResp = request(
                HttpMethod.PUT,
                "/api/users/me",
                token,
                Map.of("identityStatement", "I am a calm finisher")
        );
        assertTrue(updateMeResp.getStatusCode().is2xxSuccessful());
        assertTrue(updateMeResp.getBody().contains("I am a calm finisher"));

        assertTrue(request(HttpMethod.GET, "/api/users/stats", token, null).getStatusCode().is2xxSuccessful());
        assertTrue(request(HttpMethod.GET, "/api/users/stats/advanced", token, null).getStatusCode().is2xxSuccessful());
        assertTrue(request(HttpMethod.GET, "/api/users/badges", token, null).getStatusCode().is2xxSuccessful());

        ResponseEntity<String> createGoalResp = request(
                HttpMethod.POST,
                "/api/goals",
                token,
                Map.of(
                        "name", "Run 5K",
                        "description", "Build a gentle running routine",
                        "startDate", LocalDate.now().toString(),
                        "endDate", LocalDate.now().plusMonths(1).toString(),
                        "status", "ACTIVE"
                )
        );
        assertTrue(createGoalResp.getStatusCode().is2xxSuccessful());
        long goalId = json(createGoalResp).path("id").asLong();
        assertTrue(goalId > 0);

        ResponseEntity<String> getGoalsResp = request(HttpMethod.GET, "/api/goals", token, null);
        assertTrue(getGoalsResp.getStatusCode().is2xxSuccessful());
        assertTrue(getGoalsResp.getBody().contains("Run 5K"));

        assertTrue(request(HttpMethod.GET, "/api/goals/" + goalId, token, null).getStatusCode().is2xxSuccessful());

        ResponseEntity<String> createHabitResp = request(
                HttpMethod.POST,
                "/api/habits",
                token,
                Map.of(
                        "name", "Morning Walk",
                        "twoMinuteVersion", "Walk for 2 minutes",
                        "cueImplementationIntention", "07:30 near home",
                        "cueHabitStack", "After brushing teeth",
                        "goalId", goalId
                )
        );
        assertTrue(createHabitResp.getStatusCode().is2xxSuccessful());
        long habitId = json(createHabitResp).path("id").asLong();
        assertTrue(habitId > 0);

        ResponseEntity<String> habitsResp = request(HttpMethod.GET, "/api/habits", token, null);
        assertTrue(habitsResp.getStatusCode().is2xxSuccessful());
        assertTrue(habitsResp.getBody().contains("Morning Walk"));

        ResponseEntity<String> updateHabitResp = request(
                HttpMethod.PUT,
                "/api/habits/" + habitId,
                token,
                Map.of(
                        "name", "Morning Walk Updated",
                        "twoMinuteVersion", "Walk 2 minutes",
                        "cueImplementationIntention", "08:00 near home",
                        "cueHabitStack", "After coffee"
                )
        );
        assertTrue(updateHabitResp.getStatusCode().is2xxSuccessful());
        assertTrue(updateHabitResp.getBody().contains("Morning Walk Updated"));

        assertTrue(patchWithoutBody("/api/habits/" + habitId + "/status", token).is2xxSuccessful());
        assertTrue(patchWithoutBody("/api/habits/" + habitId + "/status", token).is2xxSuccessful());

        assertTrue(request(HttpMethod.POST, "/api/habits/" + habitId + "/complete", token, null).getStatusCode().is2xxSuccessful());

        ResponseEntity<String> completionsResp = request(HttpMethod.GET, "/api/habits/completions", token, null);
        assertTrue(completionsResp.getStatusCode().is2xxSuccessful());
        assertTrue(completionsResp.getBody().contains(LocalDate.now().toString()));

        ResponseEntity<String> habitStatsResp = request(HttpMethod.GET, "/api/habits/" + habitId + "/stats", token, null);
        assertTrue(habitStatsResp.getStatusCode().is2xxSuccessful());
        assertTrue(habitStatsResp.getBody().contains("currentStreak"));

        assertTrue(request(HttpMethod.DELETE, "/api/habits/" + habitId + "/complete", token, null).getStatusCode().is2xxSuccessful());

        ResponseEntity<String> batchHabitResp = request(
                HttpMethod.POST,
                "/api/habits/batch",
                token,
                List.of(
                        Map.of(
                                "name", "Read 2 pages",
                                "twoMinuteVersion", "Read 1 paragraph",
                                "cueImplementationIntention", "21:00 on bed",
                                "cueHabitStack", "After setting alarm"
                        ),
                        Map.of(
                                "name", "Drink water",
                                "twoMinuteVersion", "One sip",
                                "cueImplementationIntention", "08:00 at desk",
                                "cueHabitStack", "After opening laptop"
                        )
                )
        );
        assertTrue(batchHabitResp.getStatusCode().is2xxSuccessful());

        ResponseEntity<String> addHabitsToGoalResp = request(
                HttpMethod.POST,
                "/api/goals/" + goalId + "/habits",
                token,
                List.of(
                        Map.of(
                                "name", "Goal-linked stretch",
                                "twoMinuteVersion", "Stretch 2 minutes",
                                "cueImplementationIntention", "After lunch",
                                "cueHabitStack", "After eating"
                        )
                )
        );
        assertTrue(addHabitsToGoalResp.getStatusCode().is2xxSuccessful());
        assertTrue(addHabitsToGoalResp.getBody().contains("Goal-linked stretch"));

        assertTrue(request(HttpMethod.DELETE, "/api/habits/" + habitId, token, null).getStatusCode().is2xxSuccessful());

        ResponseEntity<String> gratitudeResp = request(
                HttpMethod.POST,
                "/api/moods",
                token,
                Map.of(
                        "moodType", "GRATITUDE",
                        "note", "Had a calm morning."
                )
        );
        assertTrue(gratitudeResp.getStatusCode().is2xxSuccessful());
        long gratitudeId = json(gratitudeResp).path("id").asLong();
        assertTrue(gratitudeId > 0);

        ResponseEntity<String> overwhelmedResp = request(
                HttpMethod.POST,
                "/api/moods",
                token,
                Map.of(
                        "moodType", "OVERWHELMED",
                        "note", "A lot of meetings."
                )
        );
        assertTrue(overwhelmedResp.getStatusCode().is2xxSuccessful());
        long overwhelmedId = json(overwhelmedResp).path("id").asLong();
        assertTrue(overwhelmedId > 0);

        assertTrue(request(HttpMethod.GET, "/api/moods?page=0&size=5", token, null).getStatusCode().is2xxSuccessful());
        assertTrue(request(HttpMethod.GET, "/api/moods/recent?hours=24", token, null).getStatusCode().is2xxSuccessful());

        ResponseEntity<String> invalidHoursResp = request(HttpMethod.GET, "/api/moods/recent?hours=abc", token, null);
        assertTrue(invalidHoursResp.getStatusCode().is4xxClientError());
        assertTrue(invalidHoursResp.getBody().contains("Invalid hours"));

        ResponseEntity<String> invalidSinceResp = request(HttpMethod.GET, "/api/moods/since?since=bad-date", token, null);
        assertTrue(invalidSinceResp.getStatusCode().is4xxClientError());
        assertTrue(invalidSinceResp.getBody().contains("Invalid since"));

        String since = LocalDateTime.now().minusDays(1).withNano(0).toString();
        assertTrue(request(HttpMethod.GET, "/api/moods/since?since=" + since, token, null).getStatusCode().is2xxSuccessful());

        ResponseEntity<String> gratitudeLogsResp = request(HttpMethod.GET, "/api/moods/gratitude", token, null);
        assertTrue(gratitudeLogsResp.getStatusCode().is2xxSuccessful());
        assertTrue(gratitudeLogsResp.getBody().contains("GRATITUDE"));

        ResponseEntity<String> todayGratitudeResp = request(HttpMethod.GET, "/api/moods/gratitude/today", token, null);
        assertTrue(todayGratitudeResp.getStatusCode().is2xxSuccessful());

        ResponseEntity<String> updateMoodResp = request(
                HttpMethod.PUT,
                "/api/moods/" + gratitudeId,
                token,
                Map.of("note", "Had a calm and focused morning.")
        );
        assertTrue(updateMoodResp.getStatusCode().is2xxSuccessful());
        assertTrue(updateMoodResp.getBody().contains("focused"));

        assertTrue(request(HttpMethod.DELETE, "/api/moods/" + overwhelmedId, token, null).getStatusCode().is2xxSuccessful());

        assertTrue(request(HttpMethod.GET, "/api/coach/greeting", token, null).getStatusCode().is2xxSuccessful());

        ResponseEntity<String> chatResp = request(
                HttpMethod.POST,
                "/api/coach/chat",
                token,
                Map.of("message", "Help me start small today")
        );
        assertTrue(chatResp.getStatusCode().is2xxSuccessful());
        assertNotNull(chatResp.getBody());

        assertTrue(request(HttpMethod.POST, "/api/coach/weekly-review", token, null).getStatusCode().is2xxSuccessful());
        assertTrue(request(HttpMethod.GET, "/api/coach/weekly-reviews?limit=5", token, null).getStatusCode().is2xxSuccessful());
        assertTrue(request(HttpMethod.GET, "/api/coach/history", token, null).getStatusCode().is2xxSuccessful());
        assertTrue(request(HttpMethod.GET, "/api/coach/memories", token, null).getStatusCode().is2xxSuccessful());
        assertTrue(request(HttpMethod.GET, "/api/coach/memory-hits", token, null).getStatusCode().is2xxSuccessful());

        ResponseEntity<String> notificationResp = request(HttpMethod.POST, "/api/notifications/test", token, null);
        assertTrue(notificationResp.getStatusCode().is2xxSuccessful());
    }

    private ResponseEntity<String> request(HttpMethod method, String path, String token, Object body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token);
        }

        HttpEntity<String> entity;
        if (body == null) {
            entity = new HttpEntity<>(headers);
        } else {
            entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        }

        return restTemplate.exchange(url(path), method, entity, String.class);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    private org.springframework.http.HttpStatusCode patchWithoutBody(String path, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url(path)))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .method("PATCH", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        return org.springframework.http.HttpStatusCode.valueOf(response.statusCode());
    }
}
