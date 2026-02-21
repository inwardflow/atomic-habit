package com.atomichabits.backend.service;

import com.atomichabits.backend.dto.BadgeResponse;
import com.atomichabits.backend.model.Badge;
import com.atomichabits.backend.model.User;
import com.atomichabits.backend.repository.BadgeRepository;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import com.atomichabits.backend.model.HabitCompletion;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GamificationService {

    private final BadgeRepository badgeRepository;
    private final MessageSource messageSource;

    public GamificationService(BadgeRepository badgeRepository, MessageSource messageSource) {
        this.badgeRepository = badgeRepository;
        this.messageSource = messageSource;
    }

    public void checkAndAwardBadges(User user, int currentStreak, int totalCompletions, List<HabitCompletion> completions) {
        // Streak Badges
        if (currentStreak >= 3) {
            awardBadgeIfNotExists(user, "badge.streak.3.title", "badge.streak.3.desc", "flame");
        }
        if (currentStreak >= 7) {
            awardBadgeIfNotExists(user, "badge.streak.7.title", "badge.streak.7.desc", "zap");
        }
        if (currentStreak >= 14) {
            awardBadgeIfNotExists(user, "badge.streak.14.title", "badge.streak.14.desc", "zap-filled");
        }
        if (currentStreak >= 30) {
            awardBadgeIfNotExists(user, "badge.streak.30.title", "badge.streak.30.desc", "diamond");
        }

        // Total Completion Badges
        if (totalCompletions >= 1) {
            awardBadgeIfNotExists(user, "badge.count.1.title", "badge.count.1.desc", "footprints");
        }
        if (totalCompletions >= 10) {
            awardBadgeIfNotExists(user, "badge.count.10.title", "badge.count.10.desc", "arrow-up");
        }
        if (totalCompletions >= 50) {
            awardBadgeIfNotExists(user, "badge.count.50.title", "badge.count.50.desc", "star");
        }
        if (totalCompletions >= 100) {
            awardBadgeIfNotExists(user, "badge.count.100.title", "badge.count.100.desc", "trophy");
        }

        // Time-based Badges
        checkTimeBasedBadges(user, completions);
    }

    private void checkTimeBasedBadges(User user, List<HabitCompletion> completions) {
        boolean hasEarlyMorning = completions.stream()
                .anyMatch(c -> {
                    LocalTime time = c.getCompletedAt().toLocalTime();
                    return time.isAfter(LocalTime.of(5, 0)) && time.isBefore(LocalTime.of(8, 0));
                });
        
        if (hasEarlyMorning) {
            awardBadgeIfNotExists(user, "badge.time.early.title", "badge.time.early.desc", "sunrise");
        }

        boolean hasLateNight = completions.stream()
                .anyMatch(c -> {
                    LocalTime time = c.getCompletedAt().toLocalTime();
                    return time.isAfter(LocalTime.of(22, 0)) || time.isBefore(LocalTime.of(2, 0));
                });

        if (hasLateNight) {
            awardBadgeIfNotExists(user, "badge.time.late.title", "badge.time.late.desc", "moon");
        }

        // Weekend Warrior: Completed habits on both Sat and Sun in the same weekend?
        // Simplified: Has completed habits on a Saturday AND a Sunday (ever)
        Set<DayOfWeek> days = completions.stream()
                .map(c -> c.getCompletedAt().getDayOfWeek())
                .collect(Collectors.toSet());
        
        if (days.contains(DayOfWeek.SATURDAY) && days.contains(DayOfWeek.SUNDAY)) {
            awardBadgeIfNotExists(user, "badge.time.weekend.title", "badge.time.weekend.desc", "calendar");
        }
    }

    private void awardBadgeIfNotExists(User user, String name, String description, String icon) {
        if (!badgeRepository.existsByUserIdAndName(user.getId(), name)) {
            Badge badge = Badge.builder()
                    .user(user)
                    .name(name)
                    .description(description)
                    .icon(icon)
                    .build();
            badgeRepository.save(badge);
        }
    }

    public List<Badge> getUserBadges(Long userId) {
        return badgeRepository.findByUserId(userId);
    }

    public List<BadgeResponse> getLocalizedUserBadges(Long userId) {
        return getUserBadges(userId).stream()
                .map(this::mapToLocalizedResponse)
                .collect(Collectors.toList());
    }

    private BadgeResponse mapToLocalizedResponse(Badge badge) {
        return BadgeResponse.builder()
                .id(badge.getId())
                .name(messageSource.getMessage(badge.getName(), null, badge.getName(), LocaleContextHolder.getLocale()))
                .description(messageSource.getMessage(badge.getDescription(), null, badge.getDescription(), LocaleContextHolder.getLocale()))
                .icon(badge.getIcon())
                .earnedAt(badge.getEarnedAt())
                .build();
    }
}
