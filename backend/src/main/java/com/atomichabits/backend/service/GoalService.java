package com.atomichabits.backend.service;

import com.atomichabits.backend.dto.GoalRequest;
import com.atomichabits.backend.dto.GoalResponse;
import com.atomichabits.backend.dto.HabitRequest;
import com.atomichabits.backend.dto.HabitResponse;
import com.atomichabits.backend.exception.ResourceNotFoundException;
import com.atomichabits.backend.exception.UnauthorizedException;
import com.atomichabits.backend.model.Goal;
import com.atomichabits.backend.model.Habit;
import com.atomichabits.backend.model.User;
import com.atomichabits.backend.repository.GoalRepository;
import com.atomichabits.backend.repository.HabitCompletionRepository;
import com.atomichabits.backend.repository.HabitRepository;
import com.atomichabits.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GoalService {

    private final GoalRepository goalRepository;
    private final HabitRepository habitRepository;
    private final HabitCompletionRepository habitCompletionRepository;
    private final UserRepository userRepository;
    private final HabitService habitService;

    public GoalService(GoalRepository goalRepository, HabitRepository habitRepository, HabitCompletionRepository habitCompletionRepository, UserRepository userRepository, HabitService habitService) {
        this.goalRepository = goalRepository;
        this.habitRepository = habitRepository;
        this.habitCompletionRepository = habitCompletionRepository;
        this.userRepository = userRepository;
        this.habitService = habitService;
    }

    @Transactional
    public GoalResponse createGoal(String email, GoalRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Goal goal = Goal.builder()
                .user(user)
                .name(request.getName())
                .description(request.getDescription())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(request.getStatus() != null ? request.getStatus() : "IN_PROGRESS")
                .build();

        Goal savedGoal = goalRepository.save(goal);
        
        if (request.getHabits() != null && !request.getHabits().isEmpty()) {
            final Goal goalForHabits = savedGoal;
            List<Habit> habits = request.getHabits().stream()
                .map(hRequest -> Habit.builder()
                        .user(user)
                        .goal(goalForHabits)
                        .name(hRequest.getName())
                        .twoMinuteVersion(hRequest.getTwoMinuteVersion())
                        .cueImplementationIntention(hRequest.getCueImplementationIntention())
                        .cueHabitStack(hRequest.getCueHabitStack())
                        .isActive(true)
                        .build())
                .collect(Collectors.toList());
            habitRepository.saveAll(habits);
            // Re-fetch to include habits in response or manually set them
            // Since we return mapToResponse(savedGoal), and savedGoal.getHabits() is null/empty because we didn't add to the list
            // Let's refetch
            savedGoal = goalRepository.findById(savedGoal.getId()).orElse(savedGoal);
        }

        return mapToResponse(savedGoal);
    }

    public List<GoalResponse> getUserGoals(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return goalRepository.findByUserId(user.getId()).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public GoalResponse getGoal(Long goalId, String email) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new ResourceNotFoundException("Goal not found"));
        
        if (!goal.getUser().getEmail().equals(email)) {
             throw new UnauthorizedException("You are not authorized to view this goal");
        }
        return mapToResponse(goal);
    }

    @Transactional
    public GoalResponse addHabitsToGoal(Long goalId, List<HabitRequest> habitRequests, String email) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new ResourceNotFoundException("Goal not found"));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!goal.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("You are not authorized to modify this goal");
        }

        List<Habit> habits = habitRequests.stream()
                .map(request -> Habit.builder()
                        .user(user)
                        .goal(goal)
                        .name(request.getName())
                        .twoMinuteVersion(request.getTwoMinuteVersion())
                        .cueImplementationIntention(request.getCueImplementationIntention())
                        .cueHabitStack(request.getCueHabitStack())
                        .isActive(true)
                        .build())
                .collect(Collectors.toList());

        habitRepository.saveAll(habits);
        
        // Refresh goal to get updated habits list
        // Since we saved habits separately and mappedBy is used, we might need to rely on the return value
        // But let's just refetch or reconstruct
        // The mappedBy="goal" in Goal entity means Goal doesn't own the relationship, Habit does.
        // So saving Habits with Goal set is correct.
        
        // To return the updated goal with habits, we can just fetch the habits for this goal
        // or rely on Hibernate session if we are in transaction.
        // Since we just saved, let's just manually add to the response for efficiency
        
        // However, standard way is to return the updated entity.
        // Let's refetch to be safe and consistent
        
        return getGoal(goalId, email);
    }

    private GoalResponse mapToResponse(Goal goal) {
        // We need to map habits too.
        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime endOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
        
        List<HabitResponse> habitResponses = new ArrayList<>();
        if (goal.getHabits() != null) {
            habitResponses = goal.getHabits().stream()
                    .map(habit -> {
                        boolean completedToday = habitCompletionRepository.existsByHabitIdAndCompletedAtBetween(
                                habit.getId(), startOfDay, endOfDay);
                        return HabitResponse.builder()
                            .id(habit.getId())
                            .name(habit.getName())
                            .twoMinuteVersion(habit.getTwoMinuteVersion())
                            .cueImplementationIntention(habit.getCueImplementationIntention())
                            .cueHabitStack(habit.getCueHabitStack())
                            .isActive(habit.isActive())
                            .completedToday(completedToday)
                            .scheduledToday(habitService.isScheduledForDate(habit, LocalDate.now()))
                            .frequency(null) // frequency is handled at the habit level
                            .createdAt(habit.getCreatedAt())
                            .build();
                    })
                    .collect(Collectors.toList());
        }

        return GoalResponse.builder()
                .id(goal.getId())
                .name(goal.getName())
                .description(goal.getDescription())
                .startDate(goal.getStartDate())
                .endDate(goal.getEndDate())
                .status(goal.getStatus())
                .habits(habitResponses)
                .createdAt(goal.getCreatedAt())
                .build();
    }
}
