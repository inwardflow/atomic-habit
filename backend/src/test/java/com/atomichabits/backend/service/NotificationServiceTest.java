package com.atomichabits.backend.service;

import com.atomichabits.backend.model.Habit;
import com.atomichabits.backend.model.User;
import com.atomichabits.backend.repository.HabitCompletionRepository;
import com.atomichabits.backend.repository.HabitRepository;
import com.atomichabits.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private HabitRepository habitRepository;

    @Mock
    private HabitCompletionRepository habitCompletionRepository;

    @Mock
    private HabitService habitService;

    @InjectMocks
    private NotificationService notificationService;

    private User user;
    private Habit habit1;
    private Habit habit2;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("test@example.com").build();
        habit1 = Habit.builder().id(101L).name("Habit 1").user(user).isActive(true).build();
        habit2 = Habit.builder().id(102L).name("Habit 2").user(user).isActive(true).build();
    }

    @Test
    void sendDailyReminders_ShouldCheckHabits() {
        // Arrange
        when(userRepository.findAll()).thenReturn(Collections.singletonList(user));
        when(habitRepository.findByUserIdAndIsActiveTrue(user.getId())).thenReturn(Arrays.asList(habit1, habit2));
        
        // Habit 1 completed, Habit 2 not completed
        when(habitCompletionRepository.existsByHabitIdAndCompletedAtBetween(eq(101L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(true);
        when(habitCompletionRepository.existsByHabitIdAndCompletedAtBetween(eq(102L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(false);
        
        // Mock streak calculation for uncompleted habit
        when(habitCompletionRepository.findByHabitIdOrderByCompletedAtDesc(eq(102L)))
                .thenReturn(Collections.emptyList());
        when(habitService.calculateCurrentStreak(anyList())).thenReturn(0);

        // Act
        notificationService.sendDailyReminders();

        // Assert
        verify(habitCompletionRepository, times(2)).existsByHabitIdAndCompletedAtBetween(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class));
    }
    
    @Test
    void sendDailyReminders_ShouldNotCheckIfNoActiveHabits() {
        // Arrange
        when(userRepository.findAll()).thenReturn(Collections.singletonList(user));
        when(habitRepository.findByUserIdAndIsActiveTrue(user.getId())).thenReturn(Collections.emptyList());

        // Act
        notificationService.sendDailyReminders();

        // Assert
        verify(habitCompletionRepository, never()).existsByHabitIdAndCompletedAtBetween(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class));
    }
}
