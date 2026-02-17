package com.atomichabits.backend.agent;

import io.agentscope.core.agent.Agent;
import io.agentscope.spring.boot.agui.common.ThreadSessionManager;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

public class TrackingThreadSessionManager extends ThreadSessionManager {

    private final Map<Agent, String> threadIdByAgent = Collections.synchronizedMap(new WeakHashMap<>());

    public TrackingThreadSessionManager(int maxSessions, int sessionTimeoutMinutes) {
        super(maxSessions, sessionTimeoutMinutes);
    }

    @Override
    public Agent getOrCreateAgent(String threadId, String agentId, Supplier<Agent> factory) {
        Agent agent = super.getOrCreateAgent(threadId, agentId, factory);
        if (agent != null) {
            threadIdByAgent.put(agent, threadId);
        }
        return agent;
    }

    @Override
    public boolean removeSession(String threadId) {
        getSession(threadId).ifPresent(session -> threadIdByAgent.remove(session.getAgent()));
        return super.removeSession(threadId);
    }

    @Override
    public void clear() {
        super.clear();
        threadIdByAgent.clear();
    }

    public String findThreadIdByAgent(Agent agent) {
        if (agent == null) {
            return null;
        }
        return threadIdByAgent.get(agent);
    }
}
