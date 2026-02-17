package com.atomichabits.backend.controller;

import com.atomichabits.backend.dto.HabitRequest;
import com.atomichabits.backend.dto.HabitResponse;
import com.atomichabits.backend.repository.GoalRepository;
import com.atomichabits.backend.repository.HabitCompletionRepository;
import com.atomichabits.backend.repository.HabitRepository;
import com.atomichabits.backend.repository.UserRepository;
import com.atomichabits.backend.security.CustomUserDetailsService;
import com.atomichabits.backend.security.JwtAuthenticationFilter;
import com.atomichabits.backend.security.JwtTokenProvider;
import com.atomichabits.backend.service.HabitService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HabitController.class)
@AutoConfigureMockMvc
@Import(HabitControllerTest.TestConfig.class)
class HabitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HabitService habitService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    // Mocks for CommandLineRunner in Application class
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

    @Autowired
    private ObjectMapper objectMapper;

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
    void createHabit_Success() throws Exception {
        HabitRequest request = new HabitRequest();
        request.setName("New Habit");
        request.setTwoMinuteVersion("Start small");

        HabitResponse response = HabitResponse.builder()
                .id(1L)
                .name("New Habit")
                .twoMinuteVersion("Start small")
                .isActive(true)
                .build();

        given(habitService.createHabit(eq("test@example.com"), any(HabitRequest.class)))
                .willReturn(response);

        mockMvc.perform(post("/api/habits")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("New Habit"));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void getHabits_Success() throws Exception {
        HabitResponse response = HabitResponse.builder()
                .id(1L)
                .name("New Habit")
                .isActive(true)
                .build();

        given(habitService.getUserHabits("test@example.com"))
                .willReturn(Arrays.asList(response));

        mockMvc.perform(get("/api/habits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("New Habit"));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void completeHabit_Success() throws Exception {
        mockMvc.perform(post("/api/habits/1/complete").with(csrf()))
                .andExpect(status().isOk());
    }
}
