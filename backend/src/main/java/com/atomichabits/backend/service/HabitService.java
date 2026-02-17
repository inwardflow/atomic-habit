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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class HabitService {

    private final HabitRepository habitRepository;
    private final HabitCompletionRepository habitCompletionRepository;
    private final UserRepository userRepository;

    public HabitService(HabitRepository habitRepository, HabitCompletionRepository habitCompletionRepository, UserRepository userRepository) {
        this.habitRepository = habitRepository;
        this.habitCompletionRepository = habitCompletionRepository;
        this.userRepository = userRepository;
    }

    public HabitResponse createHabit(String email, HabitRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Habit habit = Habit.builder()
                .user(user)
                .name(request.getName())
                .twoMinuteVersion(request.getTwoMinuteVersion())
                .cueImplementationIntention(request.getCueImplementationIntention())
                .cueHabitStack(request.getCueHabitStack())
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

        Habit updatedHabit = habitRepository.save(habit);
        
        // Check completion status for response
        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime endOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
        boolean completedToday = habitCompletionRepository.existsByHabitIdAndCompletedAtBetween(
                habit.getId(), startOfDay, endOfDay);
        
        List<HabitCompletion> completions = habitCompletionRepository.findByHabitIdOrderByCompletedAtDesc(habit.getId());
        int currentStreak = calculateCurrentStreak(completions);
        
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
        int currentStreak = calculateCurrentStreak(completions);
        
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
        
        // Delete completions first (if not cascading)
        // Assuming JPA/DB cascade might not be set, let's be safe or rely on DB.
        // Actually, let's just delete the habit and let Hibernate handle it if configured, 
        // or manually delete completions if needed. 
        // For safety in this "Anti-Anxiety" app, maybe we just set it to inactive? 
        // But user specifically requested DELETE.
        // Let's try hard delete. If it fails due to FK, we'll know.
        // Better: delete completions first to be sure.
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
        java.util.Map<Long, List<HabitCompletion>> completionsByHabit = allCompletions.stream()
                .collect(Collectors.groupingBy(c -> c.getHabit().getId()));

        return habitRepository.findByUserId(user.getId()).stream()
                .map(habit -> {
                    List<HabitCompletion> habitCompletions = completionsByHabit.getOrDefault(habit.getId(), java.util.Collections.emptyList());
                    
                    boolean completedToday = habitCompletions.stream()
                            .anyMatch(c -> !c.getCompletedAt().isBefore(startOfDay) && !c.getCompletedAt().isAfter(endOfDay));
                    
                    int currentStreak = calculateCurrentStreak(habitCompletions);
                    
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
        
        // Calculate current streak
        int currentStreak = calculateCurrentStreak(completions);
        
        // Calculate longest streak
        int longestStreak = calculateLongestStreak(completions);
        
        // Calculate total completions
        int totalCompletions = completions.size();
        
        // Calculate completion rate (completions / days since creation)
        long daysSinceCreation = ChronoUnit.DAYS.between(habit.getCreatedAt().toLocalDate(), LocalDate.now()) + 1;
        double completionRate = daysSinceCreation > 0 ? (double) totalCompletions / daysSinceCreation : 0;

        return HabitStatsResponse.builder()
                .habitId(habitId)
                .currentStreak(currentStreak)
                .longestStreak(longestStreak)
                .totalCompletions(totalCompletions)
                .completionRate(completionRate)
                .build();
    }

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
        
        // Check if latest completion is today or yesterday
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

    private int calculateLongestStreak(List<HabitCompletion> completions) {
        if (completions.isEmpty()) return 0;

        List<LocalDate> dates = completions.stream()
                .map(c -> c.getCompletedAt().toLocalDate())
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        if (dates.isEmpty()) return 0;

        int maxStreak = 1;
        int currentStreak = 1;
        LocalDate prevDate = dates.get(0);

        for (int i = 1; i < dates.size(); i++) {
            LocalDate currentDate = dates.get(i);
            if (currentDate.equals(prevDate.plusDays(1))) {
                currentStreak++;
            } else {
                maxStreak = Math.max(maxStreak, currentStreak);
                currentStreak = 1;
            }
            prevDate = currentDate;
        }
        
        return Math.max(maxStreak, currentStreak);
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
                .currentStreak(currentStreak)
                .createdAt(habit.getCreatedAt())
                .build();
    }
}
