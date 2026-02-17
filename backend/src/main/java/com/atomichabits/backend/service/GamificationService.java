package com.atomichabits.backend.service;

import com.atomichabits.backend.model.Badge;
import com.atomichabits.backend.model.User;
import com.atomichabits.backend.repository.BadgeRepository;
import com.atomichabits.backend.repository.HabitCompletionRepository;
import org.springframework.stereotype.Service;

import com.atomichabits.backend.model.HabitCompletion;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GamificationService {

    private final BadgeRepository badgeRepository;
    private final HabitCompletionRepository habitCompletionRepository;

    public GamificationService(BadgeRepository badgeRepository, HabitCompletionRepository habitCompletionRepository) {
        this.badgeRepository = badgeRepository;
        this.habitCompletionRepository = habitCompletionRepository;
    }

    public void checkAndAwardBadges(User user, int currentStreak, int totalCompletions, List<HabitCompletion> completions) {
        // Streak Badges
        if (currentStreak >= 3) {
            awardBadgeIfNotExists(user, "3 Day Streak", "You've completed habits for 3 days in a row!", "flame");
        }
        if (currentStreak >= 7) {
            awardBadgeIfNotExists(user, "7 Day Streak", "You're on fire! 7 days in a row.", "zap");
        }
        if (currentStreak >= 14) {
            awardBadgeIfNotExists(user, "2 Week Streak", "Two weeks of consistency!", "zap-filled");
        }
        if (currentStreak >= 30) {
            awardBadgeIfNotExists(user, "Identity Shifter", "30 days of consistency. You are becoming your habits.", "diamond");
        }

        // Total Completion Badges
        if (totalCompletions >= 1) {
            awardBadgeIfNotExists(user, "First Step", "You completed your first habit!", "footprints");
        }
        if (totalCompletions >= 10) {
            awardBadgeIfNotExists(user, "Momentum", "10 habits down. Keep going!", "arrow-up");
        }
        if (totalCompletions >= 50) {
            awardBadgeIfNotExists(user, "Habit Master", "50 habits completed. You're building a lifestyle.", "star");
        }
        if (totalCompletions >= 100) {
            awardBadgeIfNotExists(user, "Century Club", "100 habits completed.", "trophy");
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
            awardBadgeIfNotExists(user, "Early Bird", "Completed a habit before 8 AM. Seizing the day!", "sunrise");
        }

        boolean hasLateNight = completions.stream()
                .anyMatch(c -> {
                    LocalTime time = c.getCompletedAt().toLocalTime();
                    return time.isAfter(LocalTime.of(22, 0)) || time.isBefore(LocalTime.of(2, 0));
                });

        if (hasLateNight) {
            awardBadgeIfNotExists(user, "Night Owl", "Completed a habit late at night. Dedication doesn't sleep.", "moon");
        }

        // Weekend Warrior: Completed habits on both Sat and Sun in the same weekend?
        // Simplified: Has completed habits on a Saturday AND a Sunday (ever)
        Set<DayOfWeek> days = completions.stream()
                .map(c -> c.getCompletedAt().getDayOfWeek())
                .collect(Collectors.toSet());
        
        if (days.contains(DayOfWeek.SATURDAY) && days.contains(DayOfWeek.SUNDAY)) {
            awardBadgeIfNotExists(user, "Weekend Warrior", "Habits don't take weekends off.", "calendar");
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
}
