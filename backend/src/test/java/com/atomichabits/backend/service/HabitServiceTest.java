package com.atomichabits.backend.service;

import com.atomichabits.backend.dto.HabitRequest;
import com.atomichabits.backend.dto.HabitResponse;
import com.atomichabits.backend.exception.ResourceNotFoundException;
import com.atomichabits.backend.model.Habit;
import com.atomichabits.backend.model.HabitCompletion;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HabitServiceTest {

    @Mock
    private HabitRepository habitRepository;

    @Mock
    private HabitCompletionRepository habitCompletionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private HabitService habitService;

    private User user;
    private Habit habit;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .email("test@example.com")
                .build();

        habit = Habit.builder()
                .id(1L)
                .user(user)
                .name("Test Habit")
                .isActive(true)
                .build();
    }

    @Test
    void getUserHabits_ReturnsAllHabits() {
        // Arrange
        Habit activeHabit = Habit.builder().id(1L).user(user).name("Active").isActive(true).build();
        Habit inactiveHabit = Habit.builder().id(2L).user(user).name("Inactive").isActive(false).build();
        
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(habitRepository.findByUserId(1L)).thenReturn(Arrays.asList(activeHabit, inactiveHabit));
        
        // Act
        List<HabitResponse> responses = habitService.getUserHabits("test@example.com");
        
        // Assert
        assertEquals(2, responses.size());
        assertTrue(responses.stream().anyMatch(h -> h.getName().equals("Active")));
        assertTrue(responses.stream().anyMatch(h -> h.getName().equals("Inactive")));
        // Verify we are calling findByUserId, NOT findByUserIdAndIsActiveTrue
        verify(habitRepository).findByUserId(1L);
    }

    @Test
    void createHabit_Success() {
        HabitRequest request = new HabitRequest();
        request.setName("New Habit");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> {
            Habit h = invocation.getArgument(0);
            h.setId(1L);
            return h;
        });

        HabitResponse response = habitService.createHabit("test@example.com", request);

        assertNotNull(response);
        assertEquals("New Habit", response.getName());
        verify(habitRepository).save(any(Habit.class));
    }

    @Test
    void completeHabit_Success() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(habitRepository.findById(1L)).thenReturn(Optional.of(habit));
        when(habitCompletionRepository.existsByHabitIdAndCompletedAtBetween(any(), any(), any())).thenReturn(false);

        habitService.completeHabit(1L, "test@example.com");

        verify(habitCompletionRepository).save(any(HabitCompletion.class));
    }

    @Test
    void completeHabit_AlreadyCompleted() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(habitRepository.findById(1L)).thenReturn(Optional.of(habit));
        when(habitCompletionRepository.existsByHabitIdAndCompletedAtBetween(any(), any(), any())).thenReturn(true);

        habitService.completeHabit(1L, "test@example.com");

        verify(habitCompletionRepository, never()).save(any(HabitCompletion.class));
    }

    @Test
    void completeHabit_HabitNotFound() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(habitRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> 
            habitService.completeHabit(1L, "test@example.com")
        );
    }
}
