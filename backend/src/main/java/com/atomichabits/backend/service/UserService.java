package com.atomichabits.backend.service;

import com.atomichabits.backend.dto.*;
import com.atomichabits.backend.model.HabitCompletion;
import com.atomichabits.backend.model.User;
import com.atomichabits.backend.repository.HabitCompletionRepository;
import com.atomichabits.backend.repository.UserRepository;
import com.atomichabits.backend.repository.MoodRepository;
import com.atomichabits.backend.model.MoodLog;
import com.atomichabits.backend.dto.MoodInsightDTO;
import com.atomichabits.backend.exception.ResourceNotFoundException;
import com.atomichabits.backend.exception.UnauthorizedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final HabitCompletionRepository habitCompletionRepository;
    private final GamificationService gamificationService;
    private final MoodRepository moodRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public UserService(UserRepository userRepository, HabitCompletionRepository habitCompletionRepository,
                       GamificationService gamificationService, MoodRepository moodRepository,
                       PasswordEncoder passwordEncoder, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.habitCompletionRepository = habitCompletionRepository;
        this.gamificationService = gamificationService;
        this.moodRepository = moodRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    public void changePassword(String email, String currentPassword, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new UnauthorizedException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        
        // Force logout all sessions
        refreshTokenService.deleteByUserId(user.getId());
    }

    @Transactional
    public void deleteAccount(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new UnauthorizedException("Password is incorrect");
        }

        userRepository.delete(user);
    }

    public UserProfileResponse updateProfile(String email, String identityStatement, String newEmail) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (identityStatement != null) {
            user.setIdentityStatement(identityStatement);
        }
        if (newEmail != null && !newEmail.isBlank() && !newEmail.equals(email)) {
            if (userRepository.existsByEmail(newEmail)) {
                throw new IllegalArgumentException("Email is already taken");
            }
            user.setEmail(newEmail);
        }

        User savedUser = userRepository.save(user);
        return UserProfileResponse.builder()
                .id(savedUser.getId())
                .email(savedUser.getEmail())
                .identityStatement(savedUser.getIdentityStatement())
                .createdAt(savedUser.getCreatedAt())
                .build();
    }

    public AdvancedUserStatsResponse getAdvancedStats(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<HabitCompletion> completions = habitCompletionRepository.findByHabitUserId(user.getId());

        // 1. Daily Completions (Last 30 Days)
        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        Map<LocalDate, Long> dailyCounts = completions.stream()
                .map(c -> c.getCompletedAt().toLocalDate())
                .filter(d -> !d.isBefore(thirtyDaysAgo))
                .collect(Collectors.groupingBy(d -> d, Collectors.counting()));

        List<DailyCompletionDTO> last30Days = dailyCounts.entrySet().stream()
                .map(e -> new DailyCompletionDTO(e.getKey(), e.getValue().intValue()))
                .sorted(Comparator.comparing(DailyCompletionDTO::getDate))
                .toList();

        // 2. Completions by Habit
        Map<String, Integer> byHabit = completions.stream()
                .collect(Collectors.groupingBy(c -> c.getHabit().getName(), Collectors.summingInt(c -> 1)));

        // 3. Overall Completion Rate (Days with at least one completion / 30)
        long activeDays = dailyCounts.size();
        double completionRate = (double) activeDays / 30.0;
        
        // 4. Mood Insights
        List<MoodInsightDTO> moodInsights = calculateMoodInsights(user.getId(), completions, thirtyDaysAgo);

        return AdvancedUserStatsResponse.builder()
                .last30Days(last30Days)
                .completionsByHabit(byHabit)
                .overallCompletionRate(completionRate)
                .moodInsights(moodInsights)
                .build();
    }
    
    private List<MoodInsightDTO> calculateMoodInsights(Long userId, List<HabitCompletion> completions, LocalDate since) {
        // Get mood logs for the period
        List<MoodLog> moodLogs = moodRepository.findByUserIdAndCreatedAtBetween(
                userId, since.atStartOfDay(), java.time.LocalDateTime.now());
        
        if (moodLogs.isEmpty()) return Collections.emptyList();
        
        // Group mood logs by type
        Map<String, List<MoodLog>> logsByMood = moodLogs.stream()
                .collect(Collectors.groupingBy(MoodLog::getMoodType));
        
        List<MoodInsightDTO> insights = new ArrayList<>();
        
        for (Map.Entry<String, List<MoodLog>> entry : logsByMood.entrySet()) {
            String mood = entry.getKey();
            List<MoodLog> logs = entry.getValue();
            Set<LocalDate> moodDays = logs.stream()
                    .map(l -> l.getCreatedAt().toLocalDate())
                    .collect(Collectors.toSet());
            
            // Calculate avg completions on these days
            double totalCompletionsOnMoodDays = 0;
            Map<String, Integer> habitCounts = new HashMap<>();
            
            for (LocalDate day : moodDays) {
                List<HabitCompletion> daysCompletions = completions.stream()
                        .filter(c -> c.getCompletedAt().toLocalDate().equals(day))
                        .toList();
                
                totalCompletionsOnMoodDays += daysCompletions.size();
                
                for (HabitCompletion c : daysCompletions) {
                    habitCounts.merge(c.getHabit().getName(), 1, Integer::sum);
                }
            }
            
            double avg = moodDays.isEmpty() ? 0 : totalCompletionsOnMoodDays / moodDays.size();
            
            List<String> topHabits = habitCounts.entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .limit(3)
                    .map(Map.Entry::getKey)
                    .toList();
            
            insights.add(MoodInsightDTO.builder()
                    .mood(mood)
                    .logCount(logs.size())
                    .avgCompletions(avg)
                    .topHabits(topHabits)
                    .build());
        }

        insights.sort(Comparator.comparingInt(MoodInsightDTO::getLogCount).reversed());
        
        return insights;
    }

    public UserProfileResponse getUserProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .identityStatement(user.getIdentityStatement())
                .createdAt(user.getCreatedAt())
                .build();
    }

    public UserProfileResponse updateIdentity(String email, String identityStatement) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        user.setIdentityStatement(identityStatement);
        User savedUser = userRepository.save(user);
        
        return UserProfileResponse.builder()
                .id(savedUser.getId())
                .email(savedUser.getEmail())
                .identityStatement(savedUser.getIdentityStatement())
                .createdAt(savedUser.getCreatedAt())
                .build();
    }

    public UserStatsResponse getUserStats(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        List<HabitCompletion> completions = habitCompletionRepository.findByHabitUserId(user.getId());
        
        int totalCompletions = completions.size();
        int currentStreak = calculateCurrentStreak(completions);
        int longestStreak = calculateLongestStreak(completions);
        
        // Identity Score: 10 points per completion + 50 points per day of current streak
        int identityScore = (totalCompletions * 10) + (currentStreak * 50);

        // Check and award badges
        gamificationService.checkAndAwardBadges(user, currentStreak, totalCompletions, completions);

        // Fetch badges for response
        List<BadgeResponse> badgeResponses = gamificationService.getLocalizedUserBadges(user.getId());

        return UserStatsResponse.builder()
                .identityScore(identityScore)
                .currentStreak(currentStreak)
                .longestStreak(longestStreak)
                .totalHabitsCompleted(totalCompletions)
                .badges(badgeResponses)
                .build();
    }

    private int calculateCurrentStreak(List<HabitCompletion> completions) {
        if (completions.isEmpty()) return 0;

        List<LocalDate> dates = completions.stream()
                .map(c -> c.getCompletedAt().toLocalDate())
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();

        if (dates.isEmpty()) return 0;

        LocalDate today = LocalDate.now();
        LocalDate lastCompletionDate = dates.get(0);

        if (!lastCompletionDate.equals(today) && !lastCompletionDate.equals(today.minusDays(1))) {
            return 0;
        }

        int streak = 0;
        LocalDate checkDate = lastCompletionDate;
        
        for (LocalDate date : dates) {
            if (date.equals(checkDate)) {
                streak++;
                checkDate = checkDate.minusDays(1);
            } else {
                break;
            }
        }
        return streak;
    }

    private int calculateLongestStreak(List<HabitCompletion> completions) {
        if (completions.isEmpty()) return 0;

        List<LocalDate> dates = completions.stream()
                .map(c -> c.getCompletedAt().toLocalDate())
                .distinct()
                .sorted()
                .toList();

        int maxStreak = 0;
        int currentStreak = 0;
        LocalDate previousDate = null;

        for (LocalDate date : dates) {
            if (previousDate == null) {
                currentStreak = 1;
            } else if (date.equals(previousDate.plusDays(1))) {
                currentStreak++;
            } else {
                maxStreak = Math.max(maxStreak, currentStreak);
                currentStreak = 1;
            }
            previousDate = date;
        }
        
        return Math.max(maxStreak, currentStreak);
    }
}
