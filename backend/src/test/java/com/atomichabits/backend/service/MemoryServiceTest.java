package com.atomichabits.backend.service;

import com.atomichabits.backend.model.*;
import com.atomichabits.backend.repository.*;
import com.atomichabits.backend.config.CoachPromptProperties;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemoryServiceTest {

    @Mock
    private CoachMemoryRepository memoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private MoodService moodService;

    @Mock
    private HabitCompletionRepository habitCompletionRepository;

    @Mock
    private AgentScopeClient agentScopeClient;

    @Mock
    private CoachPromptProperties promptProperties;

    @Spy
    @InjectMocks
    private MemoryService memoryService;

    @BeforeEach
    void setUp() {
    }

    @Test
    void generateSummaryForDate_ShouldGenerateAndSave_WhenDataExists() {
        // Arrange
        User user = User.builder().id(1L).email("test@example.com").build();
        LocalDate date = LocalDate.now().minusDays(1);
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(java.time.LocalTime.MAX);

        // Mock existing memory check (not found)
        when(memoryRepository.findByUserIdAndReferenceDateAndType(
                eq(1L), eq(date), eq(CoachMemory.MemoryType.DAILY_SUMMARY)))
                .thenReturn(Optional.empty());

        // Mock Data
        when(chatMessageRepository.findByUserIdAndTimestampBetweenOrderByTimestampAsc(
                eq(1L), eq(start), eq(end)))
                .thenReturn(Collections.singletonList(
                        ChatMessage.builder().role("user").content("I ran today").build()
                ));

        when(moodService.getMoodsSince(eq(1L), eq(start)))
                .thenReturn(Collections.singletonList(
                        MoodLog.builder().moodType("HAPPY").createdAt(start.plusHours(1)).build()
                ));
        
        when(habitCompletionRepository.findByHabitUserIdAndCompletedAtBetween(
                eq(1L), eq(start), eq(end)))
                .thenReturn(Collections.singletonList(
                        HabitCompletion.builder().habit(Habit.builder().name("Running").build()).build()
                ));

        // Mock AI call
        doReturn("User had a great day running.").when(memoryService).callAI(anyString());

        // Act
        memoryService.generateSummaryForDate(user, date);

        // Assert
        verify(memoryRepository).save(any(CoachMemory.class));
        verify(memoryService).callAI(anyString());
    }

    @Test
    void generateSummaryForDate_ShouldSkip_WhenNoData() {
        // Arrange
        User user = User.builder().id(1L).build();
        LocalDate date = LocalDate.now().minusDays(1);

        when(memoryRepository.findByUserIdAndReferenceDateAndType(any(), any(), any()))
                .thenReturn(Optional.empty());

        when(chatMessageRepository.findByUserIdAndTimestampBetweenOrderByTimestampAsc(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(moodService.getMoodsSince(any(Long.class), any()))
                .thenReturn(Collections.emptyList());
        when(habitCompletionRepository.findByHabitUserIdAndCompletedAtBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        // Act
        memoryService.generateSummaryForDate(user, date);

        // Assert
        verify(memoryRepository, never()).save(any());
        verify(memoryService, never()).callAI(anyString());
    }

    @Test
    void generateSummaryForDate_ShouldSkip_WhenMemoryExists() {
        // Arrange
        User user = User.builder().id(1L).build();
        LocalDate date = LocalDate.now().minusDays(1);

        when(memoryRepository.findByUserIdAndReferenceDateAndType(any(), any(), any()))
                .thenReturn(Optional.of(new CoachMemory()));

        // Act
        memoryService.generateSummaryForDate(user, date);

        // Assert
        verify(memoryRepository, never()).save(any());
        verify(chatMessageRepository, never()).findByUserIdAndTimestampBetweenOrderByTimestampAsc(any(), any(), any());
    }

    @Test
    void ingestConversationSignals_ShouldSaveStableUserSignals() {
        String email = "test@example.com";
        User user = User.builder().id(1L).email(email).build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(memoryRepository.findTop20ByUserIdAndTypeOrderByCreatedAtDesc(eq(1L), any()))
                .thenReturn(Collections.emptyList());

        List<Msg> messages = List.of(
                Msg.builder()
                        .role(MsgRole.USER)
                        .content(TextBlock.builder().text("I usually work late and prefer a short evening routine.").build())
                        .build()
        );

        int savedCount = memoryService.ingestConversationSignals(email, messages);

        assertTrue(savedCount > 0);
        verify(memoryRepository, atLeastOnce()).save(any(CoachMemory.class));
    }

    @Test
    void ingestConversationSignals_ShouldUseLlmStructuredExtractionWhenEnabled() {
        String email = "test@example.com";
        User user = User.builder().id(1L).email(email).build();
        ReflectionTestUtils.setField(memoryService, "llmExtractionEnabled", true);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(memoryRepository.findTop20ByUserIdAndTypeOrderByCreatedAtDesc(eq(1L), any()))
                .thenReturn(Collections.emptyList());
        doReturn("{\"facts\":[\"I prefer short evening routines.\"],\"insights\":[\"I struggle to start after work.\"]}")
                .when(memoryService).callAIForSignalExtraction(anyString());

        List<Msg> messages = List.of(
                Msg.builder()
                        .role(MsgRole.USER)
                        .content(TextBlock.builder().text("Coach, help me stay consistent and focused.").build())
                        .build()
        );

        int savedCount = memoryService.ingestConversationSignals(email, messages);

        assertTrue(savedCount > 0);
        verify(memoryService).callAIForSignalExtraction(anyString());
        verify(memoryRepository, atLeastOnce()).save(any(CoachMemory.class));
    }
}
