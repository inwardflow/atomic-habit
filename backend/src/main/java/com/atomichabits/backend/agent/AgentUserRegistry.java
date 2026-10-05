package com.atomichabits.backend.agent;

import io.agentscope.core.agent.Agent;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Binds a short-lived agent to the user it acts for.
 *
 * <p>AgentScope may execute tools on a worker thread, where the request's
 * {@code SecurityContextHolder} is empty. Tools look the user up here by the injected
 * {@link Agent} instead, which works regardless of the executing thread.</p>
 */
@Component
public class AgentUserRegistry {

    private final Map<Agent, String> emailByAgent = Collections.synchronizedMap(new WeakHashMap<>());

    public void bind(Agent agent, String email) {
        emailByAgent.put(agent, email);
    }

    public void unbind(Agent agent) {
        emailByAgent.remove(agent);
    }

    public String findEmail(Agent agent) {
        return agent == null ? null : emailByAgent.get(agent);
    }
}
