package com.atomichabits.backend.service;

import com.atomichabits.backend.model.MoodLog;
import com.atomichabits.backend.model.User;
import com.atomichabits.backend.repository.MoodRepository;
import com.atomichabits.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MoodServiceTest {

    @Mock
    private MoodRepository moodRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MoodService moodService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .email("test@example.com")
                .build();
    }

    @Test
    void logMood_Success() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(moodRepository.save(any(MoodLog.class))).thenAnswer(invocation -> {
            MoodLog log = invocation.getArgument(0);
            log.setId(1L);
            return log;
        });

        MoodLog result = moodService.logMood("test@example.com", "HAPPY", "Great day!");

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("HAPPY", result.getMoodType());
        assertEquals("Great day!", result.getNote());
        assertEquals(1L, result.getUserId());
        verify(moodRepository).save(any(MoodLog.class));
    }

    @Test
    void getMoodHistory_Success() {
        Pageable pageable = Pageable.unpaged();
        MoodLog log = MoodLog.builder().id(1L).moodType("HAPPY").build();
        Page<MoodLog> page = new PageImpl<>(Collections.singletonList(log));

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(moodRepository.findByUserIdOrderByCreatedAtDesc(1L, pageable)).thenReturn(page);

        Page<MoodLog> result = moodService.getMoodHistory("test@example.com", pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("HAPPY", result.getContent().get(0).getMoodType());
    }

    @Test
    void getRecentMoods_Success() {
        MoodLog log = MoodLog.builder().id(1L).moodType("HAPPY").build();
        when(moodRepository.findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(anyLong(), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(log));

        List<MoodLog> result = moodService.getRecentMoods(1L);

        assertEquals(1, result.size());
        assertEquals("HAPPY", result.get(0).getMoodType());
    }

    @Test
    void getGratitudeLogs_Success() {
        MoodLog log = MoodLog.builder().id(1L).moodType("GRATITUDE").note("Thankful").build();
        
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(moodRepository.findByUserIdAndMoodTypeOrderByCreatedAtDesc(1L, "GRATITUDE"))
                .thenReturn(Collections.singletonList(log));

        List<MoodLog> result = moodService.getGratitudeLogs("test@example.com");

        assertEquals(1, result.size());
        assertEquals("GRATITUDE", result.get(0).getMoodType());
        assertEquals("Thankful", result.get(0).getNote());
    }
}
