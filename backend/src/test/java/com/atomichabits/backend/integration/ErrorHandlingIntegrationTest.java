package com.atomichabits.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Client errors must map to 4xx responses instead of falling through to the generic 500 handler.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ErrorHandlingIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String token;

    @BeforeEach
    void login() throws Exception {
        String email = "errors+" + UUID.randomUUID() + "@example.com";
        String password = "StrongPass1!";
        exchange(HttpMethod.POST, "/api/auth/register", Map.of("email", email, "password", password));
        ResponseEntity<String> login = exchange(HttpMethod.POST, "/api/auth/login", Map.of("email", email, "password", password));
        token = objectMapper.readTree(login.getBody()).path("accessToken").asText();
    }

    @Test
    void unknownRouteReturns404() {
        ResponseEntity<String> resp = exchange(HttpMethod.GET, "/api/does-not-exist", null);

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode(), resp.getBody());
    }

    @Test
    void unsupportedMethodReturns405() {
        ResponseEntity<String> resp = exchange(HttpMethod.DELETE, "/api/users/stats", null);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, resp.getStatusCode(), resp.getBody());
    }

    @Test
    void malformedJsonReturns400() {
        ResponseEntity<String> resp = exchange(HttpMethod.POST, "/api/habits", "{not json");

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode(), resp.getBody());
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange("http://localhost:" + port + path, method, new HttpEntity<>(body, headers), String.class);
    }
}
