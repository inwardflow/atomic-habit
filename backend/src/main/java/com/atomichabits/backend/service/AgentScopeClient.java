package com.atomichabits.backend.service;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.tool.Toolkit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Slf4j
@Service
public class AgentScopeClient {

    @Value("${agentscope.model.api-key}")
    private String apiKey;

    @Value("${agentscope.model.model-name}")
    private String modelName;

    @Value("${agentscope.model.base-url:https://api.siliconflow.com/v1}")
    private String baseUrl;

    @Value("${agentscope.enabled:true}")
    private boolean agentscopeEnabled;

    @Value("${agentscope.proxy.host:}")
    private String proxyHost;

    @Value("${agentscope.proxy.port:0}")
    private int proxyPort;

    @Value("${agentscope.proxy.enabled:false}")
    private boolean proxyEnabled;

    public String call(String userMessage, String systemPrompt) {
        return call(userMessage, systemPrompt, (Object[]) null);
    }

    public String call(String userMessage, String systemPrompt, Object... tools) {
        if (!agentscopeEnabled) {
            return "AI disabled (tests).";
        }
        if (!StringUtils.hasText(apiKey)) {
            log.warn("AgentScope API Key is missing. Returning fallback response.");
            return "I am currently unable to connect to the AI service (Missing API Key). Please check your configuration.";
        }

        configureProxy();

        try {
            OpenAIChatModel model = buildModel();
            ReActAgent agent = buildAgent(model, systemPrompt, tools);

            Msg response = agent.call(Msg.builder()
                            .role(MsgRole.USER)
                            .content(TextBlock.builder().text(userMessage).build())
                            .build())
                    .block();

            return response != null ? response.getTextContent() : "";
        } catch (Exception e) {
            log.error("AI call failed: {}", e.getMessage());
            return "I am currently unable to connect to the AI service (Invalid API Key or Service Unavailable). " +
                    "Please check your backend configuration. In the meantime, I'm here to support your habit tracking!";
        }
    }

    private void configureProxy() {
        if (proxyEnabled) {
            if (proxyHost != null && !proxyHost.isBlank() && proxyPort > 0) {
                try {
                    System.setProperty("http.proxyHost", proxyHost);
                    System.setProperty("http.proxyPort", String.valueOf(proxyPort));
                    System.setProperty("https.proxyHost", proxyHost);
                    System.setProperty("https.proxyPort", String.valueOf(proxyPort));
                } catch (Exception e) {
                    log.warn("Failed to configure proxy: {}", e.getMessage());
                }
            } else {
                log.warn("Proxy is enabled but host/port is invalid — skipping proxy configuration.");
            }
        }
    }

    private OpenAIChatModel buildModel() {
        var transportConfig = io.agentscope.core.model.transport.HttpTransportConfig.builder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofMinutes(3))
                .writeTimeout(Duration.ofSeconds(30))
                .build();
        var httpTransport = io.agentscope.core.model.transport.JdkHttpTransport.builder()
                .config(transportConfig)
                .build();
        return OpenAIChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .baseUrl(baseUrl)
                .httpTransport(httpTransport)
                .build();
    }

    private ReActAgent buildAgent(OpenAIChatModel model, String systemPrompt, Object... tools) {
        var builder = ReActAgent.builder()
                .name("AtomicCoach")
                .sysPrompt(systemPrompt)
                .model(model);

        if (tools != null && tools.length > 0) {
            Toolkit toolkit = new Toolkit();
            toolkit.registration()
                    .tool(tools)
                    .apply();
            builder.toolkit(toolkit);
        }

        return builder.build();
    }
}
