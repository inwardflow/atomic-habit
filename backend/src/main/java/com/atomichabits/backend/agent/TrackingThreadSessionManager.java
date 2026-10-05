package com.atomichabits.backend.agent;

import io.agentscope.core.agent.Agent;
import io.agentscope.spring.boot.agui.common.ThreadSessionManager;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

public class TrackingThreadSessionManager extends ThreadSessionManager {

    private static final ThreadLocal<String> CREATING_THREAD_ID = new ThreadLocal<>();

    private final Map<Agent, String> threadIdByAgent = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * The thread id whose agent is being created on the current thread, or {@code null}.
     * Lets the agent factory (which receives no arguments) bind the new agent to its user.
     */
    public static String creatingThreadId() {
        return CREATING_THREAD_ID.get();
    }

    public TrackingThreadSessionManager(int maxSessions, int sessionTimeoutMinutes) {
        super(maxSessions, sessionTimeoutMinutes);
    }

    @Override
    public Agent getOrCreateAgent(String threadId, String agentId, Supplier<Agent> factory) {
        Agent agent = super.getOrCreateAgent(threadId, agentId, () -> {
            CREATING_THREAD_ID.set(threadId);
            try {
                return factory.get();
            } finally {
                CREATING_THREAD_ID.remove();
            }
        });
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
