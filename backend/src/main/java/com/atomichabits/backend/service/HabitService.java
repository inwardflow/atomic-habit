package com.atomichabits.backend.service;

import com.atomichabits.backend.dto.HabitRequest;
import com.atomichabits.backend.dto.HabitResponse;
import com.atomichabits.backend.dto.HabitStatsResponse;
import com.atomichabits.backend.exception.ResourceNotFoundException;
import com.atomichabits.backend.exception.UnauthorizedException;
import com.atomichabits.backend.model.Habit;
import com.atomichabits.backend.model.HabitCompletion;
import com.atomichabits.backend.model.User;
import com.atomichabits.backend.repository.HabitCompletionRepository;
import com.atomichabits.backend.repository.HabitRepository;
import com.atomichabits.backend.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class HabitService {

    private final HabitRepository habitRepository;
    private final HabitCompletionRepository habitCompletionRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public HabitService(HabitRepository habitRepository, HabitCompletionRepository habitCompletionRepository,
                        UserRepository userRepository, ObjectMapper objectMapper) {
        this.habitRepository = habitRepository;
        this.habitCompletionRepository = habitCompletionRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    // --- Frequency helpers ---

    private String toFrequencyJson(List<String> frequency) {
        if (frequency == null || frequency.isEmpty()) return null;
        try {
            // Normalize to uppercase
            List<String> normalized = frequency.stream()
                    .map(String::toUpperCase)
                    .collect(Collectors.toList());
            return objectMapper.writeValueAsString(normalized);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private List<String> fromFrequencyJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * Check if a habit is scheduled for a given date based on its frequency.
     * null/empty frequency = daily (always scheduled).
     */
    public boolean isScheduledForDate(Habit habit, LocalDate date) {
        List<String> freq = fromFrequencyJson(habit.getFrequencyJson());
        if (freq == null || freq.isEmpty()) return true; // daily
        String dayName = date.getDayOfWeek().name(); // e.g. "MONDAY"
        return freq.contains(dayName);
    }

    // --- CRUD ---

    public HabitResponse createHabit(String email, HabitRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Habit habit = Habit.builder()
                .user(user)
                .name(request.getName())
                .twoMinuteVersion(request.getTwoMinuteVersion())
                .cueImplementationIntention(request.getCueImplementationIntention())
                .cueHabitStack(request.getCueHabitStack())
                .frequencyJson(toFrequencyJson(request.getFrequency()))
                .isActive(true)
                .build();

        Habit savedHabit = habitRepository.save(habit);
        return mapToResponse(savedHabit, false, 0);
    }

    public List<HabitResponse> createHabits(String email, List<HabitRequest> requests) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<Habit> habits = requests.stream()
                .map(request -> Habit.builder()
                        .user(user)
                        .name(request.getName())
                        .twoMinuteVersion(request.getTwoMinuteVersion())
                        .cueImplementationIntention(request.getCueImplementationIntention())
                        .cueHabitStack(request.getCueHabitStack())
                        .frequencyJson(toFrequencyJson(request.getFrequency()))
                        .isActive(true)
                        .build())
                .collect(Collectors.toList());

        List<Habit> savedHabits = habitRepository.saveAll(habits);
        
        return savedHabits.stream()
                .map(habit -> mapToResponse(habit, false, 0))
                .collect(Collectors.toList());
    }

    public HabitResponse updateHabit(Long habitId, String email, HabitRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Habit habit = habitRepository.findById(habitId)
                .orElseThrow(() -> new ResourceNotFoundException("Habit not found"));

        if (!habit.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("You are not authorized to update this habit");
        }

        habit.setName(request.getName());
        habit.setTwoMinuteVersion(request.getTwoMinuteVersion());
        habit.setCueImplementationIntention(request.getCueImplementationIntention());
        habit.setCueHabitStack(request.getCueHabitStack());
        habit.setFrequencyJson(toFrequencyJson(request.getFrequency()));

        Habit updatedHabit = habitRepository.save(habit);
        
        // Check completion status for response
        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime endOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
        boolean completedToday = habitCompletionRepository.existsByHabitIdAndCompletedAtBetween(
                habit.getId(), startOfDay, endOfDay);
        
        List<HabitCompletion> completions = habitCompletionRepository.findByHabitIdOrderByCompletedAtDesc(habit.getId());
        int currentStreak = calculateCurrentStreak(completions, updatedHabit);
        
        return mapToResponse(updatedHabit, completedToday, currentStreak);
    }

    public HabitResponse toggleHabitStatus(Long habitId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Habit habit = habitRepository.findById(habitId)
                .orElseThrow(() -> new ResourceNotFoundException("Habit not found"));

        if (!habit.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("You are not authorized to update this habit");
        }

        habit.setActive(!habit.isActive());
        Habit savedHabit = habitRepository.save(habit);
        
        // Check completion status
        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime endOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
        boolean completedToday = habitCompletionRepository.existsByHabitIdAndCompletedAtBetween(
                habit.getId(), startOfDay, endOfDay);
        
        List<HabitCompletion> completions = habitCompletionRepository.findByHabitIdOrderByCompletedAtDesc(habit.getId());
        int currentStreak = calculateCurrentStreak(completions, savedHabit);
        
        return mapToResponse(savedHabit, completedToday, currentStreak);
    }

    @Transactional
    public void deleteHabit(Long habitId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Habit habit = habitRepository.findById(habitId)
                .orElseThrow(() -> new ResourceNotFoundException("Habit not found"));

        if (!habit.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("You are not authorized to delete this habit");
        }
        
        habitCompletionRepository.deleteAll(habitCompletionRepository.findByHabitUserId(user.getId())
                .stream().filter(c -> c.getHabit().getId().equals(habitId)).collect(Collectors.toList()));

        habitRepository.delete(habit);
    }

    public List<HabitResponse> getUserHabits(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime endOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);

        // Fetch all completions for the user to avoid N+1 queries
        List<HabitCompletion> allCompletions = habitCompletionRepository.findByHabitUserId(user.getId());
        
        // Group completions by habit ID
        Map<Long, List<HabitCompletion>> completionsByHabit = allCompletions.stream()
                .collect(Collectors.groupingBy(c -> c.getHabit().getId()));

        return habitRepository.findByUserId(user.getId()).stream()
                .map(habit -> {
                    List<HabitCompletion> habitCompletions = completionsByHabit.getOrDefault(habit.getId(), Collections.emptyList());
                    
                    boolean completedToday = habitCompletions.stream()
                            .anyMatch(c -> !c.getCompletedAt().isBefore(startOfDay) && !c.getCompletedAt().isAfter(endOfDay));
                    
                    int currentStreak = calculateCurrentStreak(habitCompletions, habit);
                    
                    return mapToResponse(habit, completedToday, currentStreak);
                })
                .collect(Collectors.toList());
    }

    public void completeHabit(Long habitId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Habit habit = habitRepository.findById(habitId)
                .orElseThrow(() -> new ResourceNotFoundException("Habit not found"));
        
        if (!habit.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("You are not authorized to complete this habit");
        }
        
        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime endOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
        
        if (habitCompletionRepository.existsByHabitIdAndCompletedAtBetween(habitId, startOfDay, endOfDay)) {
             // Already completed today
             return;
        }

        HabitCompletion completion = HabitCompletion.builder()
                .habit(habit)
                .completedAt(LocalDateTime.now())
                .build();
        
        habitCompletionRepository.save(completion);
    }

    @Transactional
    public void uncompleteHabit(Long habitId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Habit habit = habitRepository.findById(habitId)
                .orElseThrow(() -> new ResourceNotFoundException("Habit not found"));

        if (!habit.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("You are not authorized to uncomplete this habit");
        }

        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime endOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);

        habitCompletionRepository.deleteByHabitIdAndCompletedAtBetween(habitId, startOfDay, endOfDay);
    }

    public List<LocalDate> getAllCompletions(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return habitCompletionRepository.findByHabitUserId(user.getId()).stream()
                .map(c -> c.getCompletedAt().toLocalDate())
                .collect(Collectors.toList());
    }

    public HabitStatsResponse getHabitStats(Long habitId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Habit habit = habitRepository.findById(habitId)
                .orElseThrow(() -> new ResourceNotFoundException("Habit not found"));

        if (!habit.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("You are not authorized to view this habit");
        }

        List<HabitCompletion> completions = habitCompletionRepository.findByHabitIdOrderByCompletedAtDesc(habitId);
        
        int currentStreak = calculateCurrentStreak(completions, habit);
        int longestStreak = calculateLongestStreak(completions, habit);
        int totalCompletions = completions.size();
        
        // Calculate completion rate considering frequency
        long daysSinceCreation = ChronoUnit.DAYS.between(habit.getCreatedAt().toLocalDate(), LocalDate.now()) + 1;
        long scheduledDays = countScheduledDays(habit, habit.getCreatedAt().toLocalDate(), LocalDate.now());
        double completionRate = scheduledDays > 0 ? (double) totalCompletions / scheduledDays : 0;

        return HabitStatsResponse.builder()
                .habitId(habitId)
                .currentStreak(currentStreak)
                .longestStreak(longestStreak)
                .totalCompletions(totalCompletions)
                .completionRate(completionRate)
                .build();
    }

    /**
     * Count how many days between start and end (inclusive) are scheduled for this habit.
     */
    private long countScheduledDays(Habit habit, LocalDate start, LocalDate end) {
        List<String> freq = fromFrequencyJson(habit.getFrequencyJson());
        if (freq == null || freq.isEmpty()) {
            return ChronoUnit.DAYS.between(start, end) + 1;
        }
        Set<DayOfWeek> scheduledDays = freq.stream()
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toSet());
        long count = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (scheduledDays.contains(d.getDayOfWeek())) count++;
        }
        return count;
    }

    /**
     * Calculate current streak, respecting frequency schedule.
     * Only counts scheduled days; non-scheduled days are skipped.
     */
    public int calculateCurrentStreak(List<HabitCompletion> completions, Habit habit) {
        if (completions.isEmpty()) return 0;
        
        Set<LocalDate> completedDates = completions.stream()
                .map(c -> c.getCompletedAt().toLocalDate())
                .collect(Collectors.toSet());
        
        LocalDate today = LocalDate.now();
        LocalDate checkDate = today;
        
        // If today is scheduled but not completed, start from yesterday
        if (isScheduledForDate(habit, today) && !completedDates.contains(today)) {
            checkDate = today.minusDays(1);
        }
        
        int streak = 0;
        // Walk backwards, skipping non-scheduled days
        while (true) {
            if (isScheduledForDate(habit, checkDate)) {
                if (completedDates.contains(checkDate)) {
                    streak++;
                } else {
                    break;
                }
            }
            // Don't go beyond habit creation
            if (habit.getCreatedAt() != null && checkDate.isBefore(habit.getCreatedAt().toLocalDate())) {
                break;
            }
            checkDate = checkDate.minusDays(1);
            // Safety: don't go back more than 365 days
            if (ChronoUnit.DAYS.between(checkDate, today) > 365) break;
        }
        return streak;
    }

    /**
     * Overload for backward compatibility (daily habits).
     */
    public int calculateCurrentStreak(List<HabitCompletion> completions) {
        if (completions.isEmpty()) return 0;
        
        List<LocalDate> dates = completions.stream()
                .map(c -> c.getCompletedAt().toLocalDate())
                .distinct()
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
        
        if (dates.isEmpty()) return 0;

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        
        LocalDate latest = dates.get(0);
        if (!latest.equals(today) && !latest.equals(yesterday)) {
            return 0;
        }

        int streak = 1;
        LocalDate current = latest;
        
        for (int i = 1; i < dates.size(); i++) {
            LocalDate next = dates.get(i);
            if (next.equals(current.minusDays(1))) {
                streak++;
                current = next;
            } else {
                break;
            }
        }
        return streak;
    }

    private int calculateLongestStreak(List<HabitCompletion> completions, Habit habit) {
        if (completions.isEmpty()) return 0;

        Set<LocalDate> completedDates = completions.stream()
                .map(c -> c.getCompletedAt().toLocalDate())
                .collect(Collectors.toSet());

        if (completedDates.isEmpty()) return 0;

        LocalDate start = completedDates.stream().min(Comparator.naturalOrder()).get();
        LocalDate end = completedDates.stream().max(Comparator.naturalOrder()).get();

        int maxStreak = 0;
        int currentStreak = 0;

        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (!isScheduledForDate(habit, d)) continue; // skip non-scheduled days
            if (completedDates.contains(d)) {
                currentStreak++;
                maxStreak = Math.max(maxStreak, currentStreak);
            } else {
                currentStreak = 0;
            }
        }

        return maxStreak;
    }

    private HabitResponse mapToResponse(Habit habit, boolean completedToday, int currentStreak) {
        return HabitResponse.builder()
                .id(habit.getId())
                .name(habit.getName())
                .twoMinuteVersion(habit.getTwoMinuteVersion())
                .cueImplementationIntention(habit.getCueImplementationIntention())
                .cueHabitStack(habit.getCueHabitStack())
                .isActive(habit.isActive())
                .completedToday(completedToday)
                .scheduledToday(isScheduledForDate(habit, LocalDate.now()))
                .currentStreak(currentStreak)
                .frequency(fromFrequencyJson(habit.getFrequencyJson()))
                .createdAt(habit.getCreatedAt())
                .build();
    }
}
