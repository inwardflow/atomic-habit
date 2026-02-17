package com.atomichabits.backend.service;

import com.atomichabits.backend.dto.UserStatsResponse;
import com.atomichabits.backend.model.HabitCompletion;
import com.atomichabits.backend.model.User;
import com.atomichabits.backend.model.MoodLog;
import com.atomichabits.backend.repository.HabitCompletionRepository;
import com.atomichabits.backend.repository.UserRepository;
import com.atomichabits.backend.repository.MoodRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private HabitCompletionRepository habitCompletionRepository;

    @Mock
    private GamificationService gamificationService;

    @Mock
    private MoodRepository moodRepository;

    @InjectMocks
    private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .email("test@example.com")
                .build();
    }

    @Test
    void getUserStats_Empty() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(habitCompletionRepository.findByHabitUserId(1L)).thenReturn(Collections.emptyList());

        UserStatsResponse stats = userService.getUserStats("test@example.com");

        assertEquals(0, stats.getCurrentStreak());
        assertEquals(0, stats.getLongestStreak());
        assertEquals(0, stats.getTotalHabitsCompleted());
        assertEquals(0, stats.getIdentityScore());
    }

    @Test
    void getUserStats_CurrentStreak_Today() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        
        List<HabitCompletion> completions = Arrays.asList(
            createCompletion(LocalDate.now()),
            createCompletion(LocalDate.now().minusDays(1)),
            createCompletion(LocalDate.now().minusDays(2))
        );
        when(habitCompletionRepository.findByHabitUserId(1L)).thenReturn(completions);

        UserStatsResponse stats = userService.getUserStats("test@example.com");

        assertEquals(3, stats.getCurrentStreak());
        assertEquals(3, stats.getLongestStreak());
        assertEquals(3, stats.getTotalHabitsCompleted());
    }

    @Test
    void getUserStats_CurrentStreak_Yesterday() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        
        List<HabitCompletion> completions = Arrays.asList(
            createCompletion(LocalDate.now().minusDays(1)),
            createCompletion(LocalDate.now().minusDays(2))
        );
        when(habitCompletionRepository.findByHabitUserId(1L)).thenReturn(completions);

        UserStatsResponse stats = userService.getUserStats("test@example.com");

        assertEquals(2, stats.getCurrentStreak());
        assertEquals(2, stats.getLongestStreak());
    }

    @Test
    void getUserStats_StreakBroken() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        
        List<HabitCompletion> completions = Arrays.asList(
            createCompletion(LocalDate.now()),
            // Missing yesterday
            createCompletion(LocalDate.now().minusDays(2))
        );
        when(habitCompletionRepository.findByHabitUserId(1L)).thenReturn(completions);

        UserStatsResponse stats = userService.getUserStats("test@example.com");

        assertEquals(1, stats.getCurrentStreak());
        assertEquals(1, stats.getLongestStreak());
    }

    @Test
    void getUserStats_StreakBroken_LongAgo() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        
        List<HabitCompletion> completions = Arrays.asList(
            createCompletion(LocalDate.now().minusDays(5)),
            createCompletion(LocalDate.now().minusDays(6))
        );
        when(habitCompletionRepository.findByHabitUserId(1L)).thenReturn(completions);

        UserStatsResponse stats = userService.getUserStats("test@example.com");

        assertEquals(0, stats.getCurrentStreak());
        assertEquals(2, stats.getLongestStreak()); // Longest streak was 2 days
    }

    @Test
    void getUserStats_LongestStreak_Multiple() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        
        List<HabitCompletion> completions = Arrays.asList(
            // Streak 1: 2 days (Today, Yesterday)
            createCompletion(LocalDate.now()),
            createCompletion(LocalDate.now().minusDays(1)),
            
            // Gap
            
            // Streak 2: 3 days (5, 6, 7 days ago)
            createCompletion(LocalDate.now().minusDays(5)),
            createCompletion(LocalDate.now().minusDays(6)),
            createCompletion(LocalDate.now().minusDays(7))
        );
        when(habitCompletionRepository.findByHabitUserId(1L)).thenReturn(completions);

        UserStatsResponse stats = userService.getUserStats("test@example.com");

        assertEquals(2, stats.getCurrentStreak());
        assertEquals(3, stats.getLongestStreak());
    }

    @Test
    void getAdvancedStats_Success() {
        // Arrange
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        
        // Mock Habits
        com.atomichabits.backend.model.Habit habit1 = new com.atomichabits.backend.model.Habit();
        habit1.setName("Run");
        com.atomichabits.backend.model.Habit habit2 = new com.atomichabits.backend.model.Habit();
        habit2.setName("Read");

        // Mock Completions
        // Today: Run, Read (2 completions)
        HabitCompletion c1 = createCompletion(today, habit1);
        HabitCompletion c2 = createCompletion(today, habit2);
        // Yesterday: Run (1 completion)
        HabitCompletion c3 = createCompletion(yesterday, habit1);
        
        List<HabitCompletion> completions = Arrays.asList(c1, c2, c3);
        when(habitCompletionRepository.findByHabitUserId(1L)).thenReturn(completions);

        // Mock Moods
        // Today: HAPPY
        MoodLog m1 = MoodLog.builder()
                .userId(1L)
                .moodType("HAPPY")
                .createdAt(today.atStartOfDay())
                .build();
        // Yesterday: TIRED
        MoodLog m2 = MoodLog.builder()
                .userId(1L)
                .moodType("TIRED")
                .createdAt(yesterday.atStartOfDay())
                .build();
        
        List<MoodLog> moodLogs = Arrays.asList(m1, m2);
        when(moodRepository.findByUserIdAndCreatedAtBetween(any(Long.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(moodLogs);

        // Act
        com.atomichabits.backend.dto.AdvancedUserStatsResponse stats = userService.getAdvancedStats("test@example.com");

        // Assert
        // Check Daily Completions
        assertEquals(2, stats.getLast30Days().size()); // 2 days with data
        
        // Check Completions by Habit
        assertEquals(2, stats.getCompletionsByHabit().get("Run"));
        assertEquals(1, stats.getCompletionsByHabit().get("Read"));
        
        // Check Mood Insights
        // HAPPY day (Today): 2 completions -> avg 2.0
        // TIRED day (Yesterday): 1 completion -> avg 1.0
        List<com.atomichabits.backend.dto.MoodInsightDTO> insights = stats.getMoodInsights();
        assertEquals(2, insights.size());
        
        com.atomichabits.backend.dto.MoodInsightDTO happyInsight = insights.stream()
                .filter(i -> "HAPPY".equals(i.getMood()))
                .findFirst().orElseThrow();
        assertEquals(2.0, happyInsight.getAvgCompletions());
        assertEquals(1, happyInsight.getLogCount());

        com.atomichabits.backend.dto.MoodInsightDTO tiredInsight = insights.stream()
                .filter(i -> "TIRED".equals(i.getMood()))
                .findFirst().orElseThrow();
        assertEquals(1.0, tiredInsight.getAvgCompletions());
        assertEquals(1, tiredInsight.getLogCount());
    }

    private HabitCompletion createCompletion(LocalDate date) {
        return HabitCompletion.builder()
                .completedAt(date.atStartOfDay())
                .build();
    }
    
    private HabitCompletion createCompletion(LocalDate date, com.atomichabits.backend.model.Habit habit) {
        return HabitCompletion.builder()
                .completedAt(date.atStartOfDay())
                .habit(habit)
                .build();
    }
}
