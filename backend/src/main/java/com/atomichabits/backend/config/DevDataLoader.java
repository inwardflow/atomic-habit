package com.atomichabits.backend.config;

import com.atomichabits.backend.model.Goal;
import com.atomichabits.backend.model.Habit;
import com.atomichabits.backend.model.HabitCompletion;
import com.atomichabits.backend.model.User;
import com.atomichabits.backend.repository.GoalRepository;
import com.atomichabits.backend.repository.HabitCompletionRepository;
import com.atomichabits.backend.repository.HabitRepository;
import com.atomichabits.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Seeds the database with sample data for local development.
 * Only active when the "dev" profile is enabled (or when no profile is set and using H2).
 */
@Slf4j
@Configuration
@Profile({"dev", "default"})
public class DevDataLoader {

    @Bean
    public CommandLineRunner seedDemoData(
            UserRepository userRepository,
            HabitRepository habitRepository,
            GoalRepository goalRepository,
            HabitCompletionRepository habitCompletionRepository,
            PasswordEncoder passwordEncoder) {

        return args -> {
            if (userRepository.count() > 0) {
                log.info("Database already contains data — skipping seed.");
                return;
            }

            User user = User.builder()
                    .email("test@example.com")
                    .password(passwordEncoder.encode("password"))
                    .identityStatement("I am a disciplined person who gets things done.")
                    .build();
            userRepository.save(user);

            LocalDateTime now = LocalDateTime.now();

            // --- Goal 1: Learn Spanish ---
            Goal goalSpanish = createGoal(goalRepository, user,
                    "Learn Spanish",
                    "Become fluent in Spanish by practicing daily.",
                    3);

            Habit habitReading = createHabit(habitRepository, user, goalSpanish,
                    "Read 10 pages", "Read 1 page",
                    "I will read 10 pages at 9pm in my bedroom.",
                    "After I brush my teeth, I will read 10 pages.");

            seedCompletions(habitCompletionRepository, habitReading, now, 30, 0.8);

            // --- Goal 2: Fitness ---
            Goal goalFitness = createGoal(goalRepository, user,
                    "Fitness",
                    "Improve cardiovascular health and build muscle.",
                    6);

            Habit habitExercise = createHabit(habitRepository, user, goalFitness,
                    "Exercise 30 mins", "Do 5 pushups",
                    "I will exercise for 30 mins at 7am in the living room.",
                    "After I drink my coffee, I will exercise for 30 mins.");

            seedCompletions(habitCompletionRepository, habitExercise, now, 30, 0.5);

            // --- Goal 3: Mindfulness ---
            Goal goalMindfulness = createGoal(goalRepository, user,
                    "Mindfulness",
                    "Reduce stress and improve focus.",
                    12);

            Habit habitMeditate = createHabit(habitRepository, user, goalMindfulness,
                    "Meditate 10 mins", "Close eyes for 1 min",
                    "I will meditate for 10 mins at 8am in the study.",
                    "After I pour my tea, I will meditate.");

            seedCompletions(habitCompletionRepository, habitMeditate, now, 5, 1.0);

            log.info("Demo data loaded: user='test@example.com', password='password'");
        };
    }

    private Goal createGoal(GoalRepository repo, User user,
                            String name, String description, int monthsDuration) {
        Goal goal = Goal.builder()
                .user(user)
                .name(name)
                .description(description)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(monthsDuration))
                .status("IN_PROGRESS")
                .build();
        return repo.save(goal);
    }

    private Habit createHabit(HabitRepository repo, User user, Goal goal,
                              String name, String twoMinuteVersion,
                              String intention, String stack) {
        Habit habit = Habit.builder()
                .user(user)
                .goal(goal)
                .name(name)
                .twoMinuteVersion(twoMinuteVersion)
                .cueImplementationIntention(intention)
                .cueHabitStack(stack)
                .isActive(true)
                .build();
        return repo.save(habit);
    }

    private void seedCompletions(HabitCompletionRepository repo, Habit habit,
                                 LocalDateTime now, int days, double successRate) {
        for (int i = 0; i < days; i++) {
            if (Math.random() < successRate) {
                repo.save(HabitCompletion.builder()
                        .habit(habit)
                        .completedAt(now.minusDays(i))
                        .build());
            }
        }
    }
}
