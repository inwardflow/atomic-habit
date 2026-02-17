package com.atomichabits.backend.service;

import com.atomichabits.backend.agent.CoachTools;
import com.atomichabits.backend.config.CoachPromptProperties;
import com.atomichabits.backend.dto.HabitResponse;
import com.atomichabits.backend.dto.UserProfileResponse;
import com.atomichabits.backend.dto.UserStatsResponse;
import com.atomichabits.backend.model.MoodLog;
import com.atomichabits.backend.model.User;
import com.atomichabits.backend.repository.ChatMessageRepository;
import com.atomichabits.backend.repository.UserRepository;
import com.atomichabits.backend.repository.WeeklyReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CoachServiceTest {

    @Mock
    private CoachTools coachTools;

    @Mock
    private HabitService habitService;

    @Mock
    private UserService userService;

    @Mock
    private MoodService moodService;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WeeklyReviewRepository weeklyReviewRepository;

    @Mock
    private CoachPromptProperties promptProperties;

    @InjectMocks
    private CoachService coachService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(coachService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(coachService, "modelName", "test-model");
        ReflectionTestUtils.setField(coachService, "agentscopeEnabled", false);
    }

    @Test
    void weeklyReview_Success() {
        // Arrange
        String email = "test@example.com";
        Long userId = 1L;

        UserProfileResponse profile = UserProfileResponse.builder()
                .id(userId)
                .identityStatement("I am a reader")
                .build();
        
        UserStatsResponse stats = UserStatsResponse.builder()
                .currentStreak(5)
                .totalHabitsCompleted(10)
                .build();

        List<HabitResponse> habits = Collections.singletonList(
                HabitResponse.builder().name("Read").completedToday(true).build()
        );

        List<MoodLog> moods = Collections.singletonList(
                MoodLog.builder().moodType("GRATITUDE").note("Grateful for coffee").build()
        );

        when(userService.getUserProfile(email)).thenReturn(profile);
        when(userService.getUserStats(email)).thenReturn(stats);
        when(habitService.getUserHabits(email)).thenReturn(habits);
        when(moodService.getMoodsSince(eq(userId), any(LocalDateTime.class))).thenReturn(moods);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(User.builder().id(userId).email(email).build()));

        when(promptProperties.getWeeklyReviewUser()).thenReturn("User Prompt");
        when(promptProperties.getWeeklyReviewSystem()).thenReturn("System Prompt");

        // Act
        // Note: usage of AgentScope might fail if not properly mocked or configured.
        // Since AgentScope makes network calls or complex initialization, we might expect this to fail 
        // if we don't mock the internal Agent creation. 
        // However, CoachService creates the agent inside the method.
        // For this unit test, we might just want to verify the data gathering part.
        // But the method calls callAgent at the end.
        
        // If we cannot easily mock the internal Agent, we might just catch the exception 
        // or accept that the return value is the fallback message.
        String result = coachService.weeklyReview(email);

        // Assert
        assertNotNull(result);
        assertEquals("AI disabled (tests).", result);
        
        // Verify that we called the services to gather context
        verify(userService).getUserProfile(email);
        verify(userService).getUserStats(email);
        verify(habitService).getUserHabits(email);
        verify(moodService).getMoodsSince(eq(userId), any(LocalDateTime.class));
    }
}
