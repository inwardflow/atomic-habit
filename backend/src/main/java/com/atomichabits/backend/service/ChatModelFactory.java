package com.atomichabits.backend.service;

import com.atomichabits.backend.config.AiModelProperties;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.model.transport.HttpTransportConfig;
import io.agentscope.core.model.transport.JdkHttpTransport;
import io.agentscope.core.model.transport.ProxyConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Builds {@link OpenAIChatModel} instances from {@link AiModelProperties}, so the REST coach
 * ({@link AgentScopeClient}) and the AG-UI streaming agent share one model configuration.
 */
@Slf4j
@Component
public class ChatModelFactory {

    private final AiModelProperties properties;

    public ChatModelFactory(AiModelProperties properties) {
        this.properties = properties;
        AiModelProperties.Proxy proxy = properties.getProxy();
        if (proxy.isEnabled() && !proxy.isUsable()) {
            log.warn("AI proxy is enabled but host/port is invalid; model calls will not use a proxy.");
        }
    }

    /**
     * @param stream whether the model should use streaming responses
     */
    public OpenAIChatModel create(boolean stream) {
        AiModelProperties.Model model = properties.getModel();
        return OpenAIChatModel.builder()
                .apiKey(model.getApiKey())
                .modelName(model.getModelName())
                .baseUrl(model.getBaseUrl())
                .httpTransport(JdkHttpTransport.builder().config(transportConfig()).build())
                .stream(stream)
                .build();
    }

    private HttpTransportConfig transportConfig() {
        AiModelProperties.Model model = properties.getModel();
        HttpTransportConfig.Builder builder = HttpTransportConfig.builder()
                .connectTimeout(model.getConnectTimeout())
                .readTimeout(model.getReadTimeout())
                .writeTimeout(model.getWriteTimeout());
        AiModelProperties.Proxy proxy = properties.getProxy();
        if (proxy.isUsable()) {
            builder.proxy(ProxyConfig.http(proxy.getHost(), proxy.getPort()));
        }
        return builder.build();
    }
}
