package com.atomichabits.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "coach.prompts")
public class CoachPromptProperties {
    private String coldStartSystem;
    private String regularSystem;
    private String greetingUserNew;
    private String greetingUserExisting;
    private String greetingSystem;
    private String weeklyReviewUser;
    private String weeklyReviewSystem;
    private String reminderUser;
    private String reminderSystem;
}
