package com.atomichabits.backend.config;

import com.atomichabits.backend.model.User;
import com.atomichabits.backend.repository.UserRepository;
import com.atomichabits.backend.service.MemoryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.LocalDate;

@Configuration
public class MemorySeeder {

    @Bean
    @ConditionalOnProperty(prefix = "coach.memory", name = "seed-on-startup", havingValue = "true")
    @Profile("!test") // Don't run in tests
    public CommandLineRunner seedMemories(UserRepository userRepository, MemoryService memoryService) {
        return args -> {
            User user = userRepository.findByEmail("test@example.com").orElse(null);
            if (user != null) {
                // Generate summaries for the last 3 days to test memory integration
                LocalDate today = LocalDate.now();
                for (int i = 3; i >= 1; i--) {
                    LocalDate date = today.minusDays(i);
                    try {
                        System.out.println("Generating summary for " + date + "...");
                        memoryService.generateSummaryForDate(user, date);
                    } catch (Exception e) {
                        System.err.println("Failed to seed memory for " + date + ": " + e.getMessage());
                    }
                }
            }
        };
    }
}
