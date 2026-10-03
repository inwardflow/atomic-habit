package com.atomichabits.backend.security;

import com.atomichabits.backend.model.User;
import com.atomichabits.backend.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AguiThreadBindingFilterTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AguiThreadBindingFilter filter = new AguiThreadBindingFilter(userRepository, objectMapper, "/agui");

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void overwritesClientChosenThreadIdWithAuthenticatedUsersThread() throws Exception {
        when(userRepository.findByEmail("owner@example.com"))
                .thenReturn(Optional.of(User.builder().id(42L).email("owner@example.com").build()));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("owner@example.com", null, List.of()));

        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(post("/agui/run", "{\"threadId\":\"user-1\",\"runId\":\"r1\"}"), new MockHttpServletResponse(), chain);

        JsonNode forwarded = objectMapper.readTree(((HttpServletRequest) chain.getRequest()).getInputStream());
        assertThat(forwarded.path("threadId").asText()).isEqualTo("user-42");
        assertThat(forwarded.path("runId").asText()).isEqualTo("r1");
    }

    @Test
    void leavesUnauthenticatedAndNonAguiRequestsUntouched() throws Exception {
        MockHttpServletRequest anonymous = post("/agui/run", "{\"threadId\":\"user-1\"}");
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(anonymous, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isSameAs(anonymous);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("owner@example.com", null, List.of()));
        MockHttpServletRequest other = post("/api/habits", "{\"threadId\":\"user-1\"}");
        MockFilterChain otherChain = new MockFilterChain();
        filter.doFilter(other, new MockHttpServletResponse(), otherChain);
        assertThat(otherChain.getRequest()).isSameAs(other);
    }

    private static MockHttpServletRequest post(String uri, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
