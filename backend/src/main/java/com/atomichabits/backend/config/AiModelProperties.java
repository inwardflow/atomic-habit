package com.atomichabits.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Settings for the OpenAI-compatible chat model behind the AI Coach.
 *
 * <p>Bound from the {@code agentscope.*} namespace in {@code application.yml}; see
 * {@code .env.example} for the matching environment variables.</p>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "agentscope")
public class AiModelProperties {

    /** Master switch; when {@code false} no outbound model calls are made. */
    private boolean enabled = true;

    private final Model model = new Model();
    private final Proxy proxy = new Proxy();

    /** {@code true} when calls are enabled and an API key is configured. */
    public boolean isConfigured() {
        return enabled && StringUtils.hasText(model.getApiKey());
    }

    @Data
    public static class Model {
        private String apiKey;
        private String modelName = "deepseek-ai/DeepSeek-V3.2";
        private String baseUrl = "https://api.siliconflow.com/v1";
        private Duration connectTimeout = Duration.ofSeconds(30);
        private Duration readTimeout = Duration.ofMinutes(3);
        private Duration writeTimeout = Duration.ofSeconds(30);
    }

    @Data
    public static class Proxy {
        private boolean enabled;
        private String host;
        private int port;

        /** {@code true} when the proxy is enabled and points at a usable host/port. */
        public boolean isUsable() {
            return enabled && StringUtils.hasText(host) && port > 0;
        }
    }
}
