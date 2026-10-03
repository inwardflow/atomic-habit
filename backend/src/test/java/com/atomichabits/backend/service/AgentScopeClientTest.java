package com.atomichabits.backend.service;

import com.atomichabits.backend.agent.AgentUserRegistry;
import com.atomichabits.backend.config.AiModelProperties;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentScopeClientTest {

    private final AiModelProperties properties = new AiModelProperties();
    private final ChatModelFactory chatModelFactory = mock(ChatModelFactory.class);
    private final AgentScopeClient client = new AgentScopeClient(properties, chatModelFactory, new AgentUserRegistry());

    @Test
    void returnsDisabledResponseWithoutCallingModelWhenDisabled() {
        properties.setEnabled(false);
        properties.getModel().setApiKey("sk-test");

        assertThat(client.call("hi", "system")).isEqualTo(AgentScopeClient.DISABLED_RESPONSE);
        verify(chatModelFactory, never()).create(anyBoolean());
    }

    @Test
    void returnsMissingKeyResponseWithoutCallingModelWhenApiKeyBlank() {
        properties.getModel().setApiKey("  ");

        assertThat(client.call("hi", "system")).isEqualTo(AgentScopeClient.MISSING_KEY_RESPONSE);
        verify(chatModelFactory, never()).create(anyBoolean());
    }

    @Test
    void buildToolkitRegistersAnnotatedMethodsOfEachToolObject() {
        Toolkit toolkit = AgentScopeClient.buildToolkit(new GreetingTools(), new ClockTools());

        assertThat(toolkit.getToolNames()).containsExactlyInAnyOrder("greet", "now");
    }

    @Test
    void returnsFallbackInsteadOfThrowingWhenModelFails() {
        properties.getModel().setApiKey("sk-test");
        when(chatModelFactory.create(anyBoolean())).thenThrow(new IllegalStateException("boom"));

        assertThat(client.call("hi", "system")).isEqualTo(AgentScopeClient.UNAVAILABLE_RESPONSE);
    }

    static class GreetingTools {
        @Tool(name = "greet", description = "Greets someone.")
        public String greet(@ToolParam(name = "name", description = "Who to greet.") String name) {
            return "Hello " + name;
        }
    }

    static class ClockTools {
        @Tool(name = "now", description = "Returns a fixed time.")
        public String now() {
            return "12:00";
        }
    }
}
