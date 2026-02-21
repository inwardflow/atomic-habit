package com.atomichabits.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "coach.memory")
public class CoachMemoryProperties {
    private boolean llmExtractionEnabled;
    private boolean seedOnStartup;
}
