package com.atomichabits.backend.service;

import com.atomichabits.backend.dto.BadgeResponse;
import com.atomichabits.backend.model.Badge;
import com.atomichabits.backend.model.User;
import com.atomichabits.backend.repository.BadgeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GamificationServiceTest {

    @Mock
    private BadgeRepository badgeRepository;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private GamificationService gamificationService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .email("test@example.com")
                .build();
    }

    @Test
    void getLocalizedUserBadges_TranslatesCorrectly() {
        Badge badge = Badge.builder()
                .id(1L)
                .name("badge.test.title")
                .description("badge.test.desc")
                .icon("star")
                .user(user)
                .build();

        when(badgeRepository.findByUserId(1L)).thenReturn(Collections.singletonList(badge));
        
        // Mock translation
        when(messageSource.getMessage(eq("badge.test.title"), any(), eq("badge.test.title"), any(Locale.class)))
                .thenReturn("Test Badge Title");
        when(messageSource.getMessage(eq("badge.test.desc"), any(), eq("badge.test.desc"), any(Locale.class)))
                .thenReturn("Test Badge Description");

        List<BadgeResponse> responses = gamificationService.getLocalizedUserBadges(1L);

        assertEquals(1, responses.size());
        BadgeResponse response = responses.get(0);
        assertEquals("Test Badge Title", response.getName());
        assertEquals("Test Badge Description", response.getDescription());
        assertEquals("star", response.getIcon());
    }
}
