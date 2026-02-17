package com.atomichabits.backend.config;

import com.atomichabits.backend.agent.TrackingThreadSessionManager;
import io.agentscope.spring.boot.agui.common.AguiProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AguiSessionConfig {

    @Bean
    @Primary
    public TrackingThreadSessionManager trackingThreadSessionManager(AguiProperties properties) {
        return new TrackingThreadSessionManager(
                properties.getMaxThreadSessions(),
                properties.getSessionTimeoutMinutes()
        );
    }
}
