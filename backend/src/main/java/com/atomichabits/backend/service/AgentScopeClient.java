package com.atomichabits.backend.service;

import com.atomichabits.backend.agent.AgentUserRegistry;
import com.atomichabits.backend.config.AiModelProperties;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.tool.Toolkit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Synchronous, single-turn access to the AI Coach model for REST endpoints and background jobs.
 *
 * <p>Never throws: when the model is disabled, unconfigured or unreachable a friendly fallback
 * message is returned so the calling feature keeps working.</p>
 */
@Slf4j
@Service
public class AgentScopeClient {

    static final String DISABLED_RESPONSE = "AI disabled (tests).";
    static final String MISSING_KEY_RESPONSE =
            "I am currently unable to connect to the AI service (Missing API Key). Please check your configuration.";
    static final String UNAVAILABLE_RESPONSE =
            "I am currently unable to connect to the AI service (Invalid API Key or Service Unavailable). "
                    + "Please check your backend configuration. In the meantime, I'm here to support your habit tracking!";

    private final AiModelProperties properties;
    private final ChatModelFactory chatModelFactory;
    private final AgentUserRegistry agentUserRegistry;

    public AgentScopeClient(AiModelProperties properties, ChatModelFactory chatModelFactory,
                            AgentUserRegistry agentUserRegistry) {
        this.properties = properties;
        this.chatModelFactory = chatModelFactory;
        this.agentUserRegistry = agentUserRegistry;
    }

    public String call(String userMessage, String systemPrompt) {
        return call(userMessage, systemPrompt, (Object[]) null);
    }

    /**
     * @param tools objects exposing {@code @Tool}-annotated methods the agent may invoke
     */
    public String call(String userMessage, String systemPrompt, Object... tools) {
        return callAsUser(null, userMessage, systemPrompt, tools);
    }

    /**
     * Like {@link #call(String, String, Object...)}, but binds the agent to {@code userEmail} so
     * tools can identify the user even when they run outside the request thread.
     */
    public String callAsUser(String userEmail, String userMessage, String systemPrompt, Object... tools) {
        if (!properties.isEnabled()) {
            return DISABLED_RESPONSE;
        }
        if (!properties.isConfigured()) {
            log.warn("AgentScope API Key is missing. Returning fallback response.");
            return MISSING_KEY_RESPONSE;
        }

        ReActAgent agent = null;
        try {
            // Non-streaming: we block for the full reply anyway, and some OpenAI-compatible providers
            // emit malformed streamed tool_call deltas (empty name/arguments), silently dropping tool calls.
            agent = buildAgent(chatModelFactory.create(false), systemPrompt, tools);
            if (StringUtils.hasText(userEmail)) {
                agentUserRegistry.bind(agent, userEmail);
            }

            Msg response = agent.call(Msg.builder()
                            .role(MsgRole.USER)
                            .content(TextBlock.builder().text(userMessage).build())
                            .build())
                    .block();

            return response != null ? response.getTextContent() : "";
        } catch (Exception e) {
            log.error("AI call failed: {}", e.getMessage(), e);
            return UNAVAILABLE_RESPONSE;
        } finally {
            if (agent != null) {
                agentUserRegistry.unbind(agent);
            }
        }
    }

    private ReActAgent buildAgent(OpenAIChatModel model, String systemPrompt, Object... tools) {
        var builder = ReActAgent.builder()
                .name("AtomicCoach")
                .sysPrompt(systemPrompt)
                .model(model);

        if (tools != null && tools.length > 0) {
            builder.toolkit(buildToolkit(tools));
        }

        return builder.build();
    }

    /**
     * Registers each tool object individually. {@code registration().tool(Object)} takes a single
     * object, so passing the varargs array directly would register no tools at all.
     */
    static Toolkit buildToolkit(Object... tools) {
        Toolkit toolkit = new Toolkit();
        for (Object tool : tools) {
            if (tool != null) {
                toolkit.registration().tool(tool).apply();
            }
        }
        return toolkit;
    }
}
