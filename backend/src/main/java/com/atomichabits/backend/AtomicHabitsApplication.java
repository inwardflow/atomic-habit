package com.atomichabits.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.atomichabits.backend.model.User;
import com.atomichabits.backend.repository.UserRepository;
import com.atomichabits.backend.model.Goal;
import com.atomichabits.backend.repository.GoalRepository;
import com.atomichabits.backend.model.Habit;
import com.atomichabits.backend.repository.HabitRepository;
import com.atomichabits.backend.model.HabitCompletion;
import com.atomichabits.backend.repository.HabitCompletionRepository;

@SpringBootApplication
@EnableScheduling
public class AtomicHabitsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AtomicHabitsApplication.class, args);
    }

    @Bean
    public CommandLineRunner dataLoader(UserRepository userRepository, HabitRepository habitRepository, GoalRepository goalRepository, HabitCompletionRepository habitCompletionRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.count() == 0) {
                // Create User
                User user = User.builder()
                    .email("test@example.com")
                    .password(passwordEncoder.encode("password"))
                    .identityStatement("I am a disciplined person who gets things done.")
                    .build();
                userRepository.save(user);

                // Goal 1: Learn Spanish
                Goal goalSpanish = Goal.builder()
                    .user(user)
                    .name("Learn Spanish")
                    .description("Become fluent in Spanish by practicing daily.")
                    .startDate(java.time.LocalDate.now())
                    .endDate(java.time.LocalDate.now().plusMonths(3))
                    .status("IN_PROGRESS")
                    .build();
                goalRepository.save(goalSpanish);

                Habit habitReading = Habit.builder()
                        .user(user)
                        .goal(goalSpanish)
                        .name("Read 10 pages")
                        .twoMinuteVersion("Read 1 page")
                        .cueImplementationIntention("I will read 10 pages at 9pm in my bedroom.")
                        .cueHabitStack("After I brush my teeth, I will read 10 pages.")
                        .isActive(true)
                        .build();
                habitRepository.save(habitReading);

                // Generate history for reading (mostly consistent)
                java.time.LocalDateTime now = java.time.LocalDateTime.now();
                for (int i = 0; i < 30; i++) {
                    if (Math.random() > 0.2) { // 80% success rate
                        habitCompletionRepository.save(HabitCompletion.builder()
                            .habit(habitReading)
                            .completedAt(now.minusDays(i))
                            .build());
                    }
                }

                // Goal 2: Fitness
                Goal goalFitness = Goal.builder()
                    .user(user)
                    .name("Fitness")
                    .description("Improve cardiovascular health and build muscle.")
                    .startDate(java.time.LocalDate.now())
                    .endDate(java.time.LocalDate.now().plusMonths(6))
                    .status("IN_PROGRESS")
                    .build();
                goalRepository.save(goalFitness);

                Habit habitExercise = Habit.builder()
                        .user(user)
                        .goal(goalFitness)
                        .name("Exercise 30 mins")
                        .twoMinuteVersion("Do 5 pushups")
                        .cueImplementationIntention("I will exercise for 30 mins at 7am in the living room.")
                        .cueHabitStack("After I drink my coffee, I will exercise for 30 mins.")
                        .isActive(true)
                        .build();
                habitRepository.save(habitExercise);

                // Generate history for exercise (struggling a bit)
                for (int i = 0; i < 30; i++) {
                    if (Math.random() > 0.5) { // 50% success rate
                        habitCompletionRepository.save(HabitCompletion.builder()
                            .habit(habitExercise)
                            .completedAt(now.minusDays(i))
                            .build());
                    }
                }

                // Goal 3: Mindfulness
                Goal goalMindfulness = Goal.builder()
                    .user(user)
                    .name("Mindfulness")
                    .description("Reduce stress and improve focus.")
                    .startDate(java.time.LocalDate.now())
                    .endDate(java.time.LocalDate.now().plusMonths(12))
                    .status("IN_PROGRESS")
                    .build();
                goalRepository.save(goalMindfulness);

                Habit habitMeditate = Habit.builder()
                        .user(user)
                        .goal(goalMindfulness)
                        .name("Meditate 10 mins")
                        .twoMinuteVersion("Close eyes for 1 min")
                        .cueImplementationIntention("I will meditate for 10 mins at 8am in the study.")
                        .cueHabitStack("After I pour my tea, I will meditate.")
                        .isActive(true)
                        .build();
                habitRepository.save(habitMeditate);

                // Generate history for meditation (new habit, started 5 days ago)
                for (int i = 0; i < 5; i++) {
                     habitCompletionRepository.save(HabitCompletion.builder()
                        .habit(habitMeditate)
                        .completedAt(now.minusDays(i))
                        .build());
                }
                
                System.out.println("Data loaded: User 'test@example.com' with password 'password'");
            }
        };
    }

}
