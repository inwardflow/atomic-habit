package com.atomichabits.backend.config;

import com.atomichabits.backend.agent.CoachTools;
import com.atomichabits.backend.agent.AgentUserRegistry;
import com.atomichabits.backend.agent.CoachLongTermMemory;
import com.atomichabits.backend.agent.TrackingThreadSessionManager;
import com.atomichabits.backend.model.User;
import com.atomichabits.backend.repository.UserRepository;
import com.atomichabits.backend.security.AguiThreadBindingFilter;
import com.atomichabits.backend.service.ChatModelFactory;
import io.agentscope.spring.boot.agui.common.AguiAgentRegistryCustomizer;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.memory.LongTermMemoryMode;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.tool.Toolkit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AguiConfig {

    private final CoachTools coachTools;
    private final CoachLongTermMemory coachLongTermMemory;
    private final ChatModelFactory chatModelFactory;
    private final AgentUserRegistry agentUserRegistry;
    private final UserRepository userRepository;

    public AguiConfig(CoachTools coachTools, CoachLongTermMemory coachLongTermMemory, ChatModelFactory chatModelFactory,
                      AgentUserRegistry agentUserRegistry, UserRepository userRepository) {
        this.coachTools = coachTools;
        this.coachLongTermMemory = coachLongTermMemory;
        this.chatModelFactory = chatModelFactory;
        this.agentUserRegistry = agentUserRegistry;
        this.userRepository = userRepository;
    }

    /**
     * Resolves the user for the agent being created from its thread id, which
     * {@link com.atomichabits.backend.security.AguiThreadBindingFilter} pins to {@code user-<id>}.
     */
    private String resolveCreatingUserEmail() {
        String threadId = TrackingThreadSessionManager.creatingThreadId();
        if (threadId == null || !threadId.startsWith(AguiThreadBindingFilter.THREAD_PREFIX)) {
            return null;
        }
        try {
            long userId = Long.parseLong(threadId.substring(AguiThreadBindingFilter.THREAD_PREFIX.length()));
            return userRepository.findById(userId).map(User::getEmail).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Bean
    public AguiAgentRegistryCustomizer aguiAgentRegistryCustomizer() {
        return registry -> registry.registerFactory("default", this::createAgent);
    }

    private Agent createAgent() {
        // Initialize Toolkit
        Toolkit toolkit = new Toolkit();
        toolkit.registration()
                .tool(coachTools)
                .apply();

        // Some provider/model combinations emit malformed streaming tool events,
        // which breaks @ag-ui/client verification and surfaces as "Connection failed".
        // Disable model-level streaming so AG-UI can emit a stable event sequence.
        OpenAIChatModel model = chatModelFactory.create(false);

        String email = resolveCreatingUserEmail();

        // Initialize Agent
        ReActAgent agent = ReActAgent.builder()
                .name("AtomicCoach")
                .sysPrompt("""
                        You are an expert AI Coach based on James Clear's 'Atomic Habits'.
                        Your goal is to help users build better habits using tiny steps and identity-based behavior change.

                        TOOL RULES:
                        - At the beginning of each new conversation, call `get_user_status` first.
                        - Long-term memory is auto-retrieved every turn. Only call `get_user_memory_context` when user asks for memory details or retrieval seems insufficient.
                        - Tools that accept `email` can be called with an empty string when unknown.
                        - Use `save_user_identity` when the user confirms identity.
                        - Use `create_first_habit` when user agrees to start.
                        - Use `complete_habit` when user says they finished a habit.
                        - Use `present_daily_focus` when user asks what to do next or feels overwhelmed.
                        - Use `log_mood` when user expresses clear emotions.
                        - Use `save_user_insight` when the user reveals recurring patterns:
                          preferences, obstacles, routines, energy patterns, motivation triggers, or coaching style preferences.
                        - Use `save_long_term_fact` only for stable facts likely to remain useful for weeks.
                        - Keep saved insight/fact to one concise sentence and avoid duplicates.
                        - Do not save one-off chatter; prioritize reusable signals that can improve future coaching decisions.

                        PLAN FORMAT RULES:
                        - If user asks for a plan/challenge, output a JSON plan in a fenced block.
                        - Prefer this exact habit shape:
                        ```json
                        [
                          {
                            "name": "Habit name",
                            "twoMinuteVersion": "2-minute version",
                            "cueImplementationIntention": "When/where I will do it",
                            "cueHabitStack": "After [existing habit], I will..."
                          }
                        ]
                        ```
                        - Keep the plan small (3-5 habits), practical, and anxiety-friendly.

                        STYLE RULES:
                        - Reply in the same language as the user's latest message (including quick replies).
                        - Keep responses concise (usually <= 3 sentences outside JSON).
                        - No shaming language.
                        - Encourage "start small" and consistency over intensity.
                        - Personalize advice by explicitly using known memories whenever relevant.

                        INTERACTIVE REPLIES:
                        - At the VERY END of each response, provide 2-3 short quick replies in:
                        ```replies ["Reply 1", "Reply 2"] ```
                        """)
                .model(model)
                .toolkit(toolkit)
                .memory(new InMemoryMemory())
                .longTermMemory(email != null ? coachLongTermMemory.forUser(email) : coachLongTermMemory)
                .longTermMemoryMode(LongTermMemoryMode.STATIC_CONTROL)
                .build();
        if (email != null) {
            agentUserRegistry.bind(agent, email);
        }
        return agent;
    }
}
