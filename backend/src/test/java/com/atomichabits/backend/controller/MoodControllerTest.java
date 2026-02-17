package com.atomichabits.backend.controller;

import com.atomichabits.backend.model.MoodLog;
import com.atomichabits.backend.repository.GoalRepository;
import com.atomichabits.backend.repository.HabitCompletionRepository;
import com.atomichabits.backend.repository.HabitRepository;
import com.atomichabits.backend.repository.UserRepository;
import com.atomichabits.backend.security.CustomUserDetailsService;
import com.atomichabits.backend.security.JwtAuthenticationFilter;
import com.atomichabits.backend.security.JwtTokenProvider;
import com.atomichabits.backend.service.MoodService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MoodController.class)
@AutoConfigureMockMvc
@Import(MoodControllerTest.TestConfig.class)
class MoodControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MoodService moodService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private UserRepository userRepository;
    @MockBean
    private HabitRepository habitRepository;
    @MockBean
    private GoalRepository goalRepository;
    @MockBean
    private HabitCompletionRepository habitCompletionRepository;
    @MockBean
    private PasswordEncoder passwordEncoder;

    @TestConfiguration
    static class TestConfig {
        @Bean
        public JwtAuthenticationFilter jwtAuthenticationFilter() {
            return new JwtAuthenticationFilter(null, null) {
                @Override
                protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
                    filterChain.doFilter(request, response);
                }
            };
        }
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void getRecentMoods_DefaultHours_Success() throws Exception {
        MoodLog log = MoodLog.builder().id(1L).moodType("HAPPY").build();
        given(moodService.getRecentMoods(eq("test@example.com"), eq(24)))
                .willReturn(Collections.singletonList(log));

        mockMvc.perform(get("/api/moods/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].moodType").value("HAPPY"));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void getRecentMoods_InvalidHours_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/moods/recent").param("hours", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid hours; expected an integer"));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void getMoodsSince_Success() throws Exception {
        MoodLog log = MoodLog.builder().id(1L).moodType("OVERWHELMED").build();
        LocalDateTime since = LocalDateTime.parse("2026-02-16T10:15:30");
        given(moodService.getMoodsSince(eq("test@example.com"), eq(since)))
                .willReturn(Collections.singletonList(log));

        mockMvc.perform(get("/api/moods/since").param("since", "2026-02-16T10:15:30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].moodType").value("OVERWHELMED"));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void getMoodsSince_InvalidSince_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/moods/since").param("since", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid since; expected ISO-8601 date-time like 2026-02-16T10:15:30"));
    }
}
