package com.atomichabits.backend.config;

import com.atomichabits.backend.model.User;
import com.atomichabits.backend.repository.UserRepository;
import com.atomichabits.backend.service.MemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.LocalDate;

@Slf4j
@Configuration
public class MemorySeeder {

    @Bean
    @ConditionalOnProperty(prefix = "coach.memory", name = "seed-on-startup", havingValue = "true")
    @Profile("!test")
    public CommandLineRunner seedMemories(UserRepository userRepository, MemoryService memoryService) {
        return args -> {
            User user = userRepository.findByEmail("test@example.com").orElse(null);
            if (user != null) {
                LocalDate today = LocalDate.now();
                for (int i = 3; i >= 1; i--) {
                    LocalDate date = today.minusDays(i);
                    try {
                        log.info("Generating memory summary for {}...", date);
                        memoryService.generateSummaryForDate(user, date);
                    } catch (Exception e) {
                        log.warn("Failed to seed memory for {}: {}", date, e.getMessage());
                    }
                }
            }
        };
    }
}
