package com.atomichabits.backend.agent;

import com.atomichabits.backend.dto.HabitRequest;
import com.atomichabits.backend.dto.HabitResponse;
import com.atomichabits.backend.dto.UserProfileResponse;
import com.atomichabits.backend.model.CoachMemory;
import com.atomichabits.backend.model.MoodLog;
import com.atomichabits.backend.repository.UserRepository;
import com.atomichabits.backend.service.HabitService;
import com.atomichabits.backend.service.MemoryService;
import com.atomichabits.backend.service.MoodService;
import com.atomichabits.backend.service.UserService;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class CoachTools {
    private static final String USER_THREAD_PREFIX = "user-";

    private final UserService userService;
    private final HabitService habitService;
    private final MoodService moodService;
    private final MemoryService memoryService;
    private final UserRepository userRepository;
    private final TrackingThreadSessionManager threadSessionManager;

    public CoachTools(UserService userService,
                      HabitService habitService,
                      MoodService moodService,
                      MemoryService memoryService,
                      UserRepository userRepository,
                      TrackingThreadSessionManager threadSessionManager) {
        this.userService = userService;
        this.habitService = habitService;
        this.moodService = moodService;
        this.memoryService = memoryService;
        this.userRepository = userRepository;
        this.threadSessionManager = threadSessionManager;
    }

    private String resolveEmail(String email, Agent agent) {
        if (StringUtils.hasText(email)) {
            return email.trim();
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String principal = authentication.getName();
            if (StringUtils.hasText(principal) && !"anonymousUser".equalsIgnoreCase(principal)) {
                return principal;
            }
        }
        return resolveEmailFromAgentThread(agent);
    }

    private String resolveEmailFromAgentThread(Agent agent) {
        if (agent == null) {
            return null;
        }

        String threadId = threadSessionManager.findThreadIdByAgent(agent);
        if (!StringUtils.hasText(threadId) || !threadId.startsWith(USER_THREAD_PREFIX)) {
            return null;
        }

        try {
            long userId = Long.parseLong(threadId.substring(USER_THREAD_PREFIX.length()));
            return userRepository.findById(userId).map(user -> user.getEmail()).orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Tool(name = "present_weekly_review", description = "Present a visual weekly review card to the user. Use this during the weekly review session.")
    public String presentWeeklyReview(
            @ToolParam(name = "totalCompleted", description = "Total number of habits completed this week.") int totalCompleted,
            @ToolParam(name = "currentStreak", description = "Current day streak.") int currentStreak,
            @ToolParam(name = "highlights", description = "List of positive highlights (strings).") List<String> highlights,
            @ToolParam(name = "suggestion", description = "A short, encouraging suggestion.") String suggestion) {
        // This tool is mainly for frontend rendering trigger.
        // The return value is just a confirmation for the LLM.
        return "Presented Weekly Review Card: " + totalCompleted + " completions, " + currentStreak + " day streak.";
    }

    @Tool(name = "complete_habit", description = "Mark a habit as completed for today. Use this when the user says they finished a habit.")
    public String completeHabit(
            @ToolParam(name = "email", description = "The user's email address. Optional when user is authenticated.") String email,
            @ToolParam(name = "habitName", description = "The name of the habit to complete.") String habitName,
            Agent agent) {
        try {
            String resolvedEmail = resolveEmail(email, agent);
            if (!StringUtils.hasText(resolvedEmail)) {
                return "Failed to complete habit: user is not authenticated.";
            }

            // Find habit by name (case-insensitive for better UX)
            List<HabitResponse> habits = habitService.getUserHabits(resolvedEmail);
            HabitResponse target = habits.stream()
                    .filter(h -> h.getName().equalsIgnoreCase(habitName))
                    .findFirst()
                    .orElse(null);
            
            if (target == null) {
                return "Error: Habit '" + habitName + "' not found.";
            }
            
            habitService.completeHabit(target.getId(), resolvedEmail);
            return "Habit '" + habitName + "' marked as completed!";
        } catch (Exception e) {
            return "Failed to complete habit: " + e.getMessage();
        }
    }

    @Tool(name = "present_daily_focus", description = "Present a single 'Focus Card' for one specific habit. Use this when the user is overwhelmed or asks what to do next.")
    public String presentDailyFocus(
            @ToolParam(name = "habitName", description = "The name of the habit to focus on.") String habitName,
            @ToolParam(name = "twoMinuteVersion", description = "The 2-minute version of the habit.") String twoMinuteVersion) {
        return "Presented Daily Focus Card for: " + habitName;
    }

    @Tool(name = "log_mood", description = "Log the user's current mood. Use this when the user explicitly expresses a feeling (e.g., 'I am tired', 'I feel great').")
    public String logMood(
            @ToolParam(name = "email", description = "The user's email address. Optional when user is authenticated.") String email,
            @ToolParam(name = "moodType", description = "The type of mood. Allowed values: MOTIVATED, FOCUSED, HAPPY, NEUTRAL, TIRED, SAD, ANXIOUS, ANGRY, GRATITUDE.") String moodType,
            @ToolParam(name = "note", description = "A short note or reason for the mood (optional).") String note,
            Agent agent) {
        try {
            String resolvedEmail = resolveEmail(email, agent);
            if (!StringUtils.hasText(resolvedEmail)) {
                return "Failed to log mood: user is not authenticated.";
            }

            String normalizedMoodType = moodType == null ? "NEUTRAL" : moodType.toUpperCase();
            moodService.logMood(resolvedEmail, normalizedMoodType, note);
            return "Mood logged: " + normalizedMoodType;
        } catch (Exception e) {
            return "Failed to log mood: " + e.getMessage();
        }
    }

    @Tool(name = "get_user_status", description = "Get the user's current habit status, recent moods, and identity. Use this to understand the user's context.")
    public String getUserStatus(
            @ToolParam(name = "email", description = "The user's email address. Optional when user is authenticated.") String email,
            Agent agent) {
        StringBuilder context = new StringBuilder();
        try {
            String resolvedEmail = resolveEmail(email, agent);
            if (!StringUtils.hasText(resolvedEmail)) {
                return "Error retrieving user status: user is not authenticated.";
            }

            UserProfileResponse profile = userService.getUserProfile(resolvedEmail);
            Long uId = profile.getId();
            
            // Identity
            if (profile.getIdentityStatement() != null) {
                context.append("Identity: ").append(profile.getIdentityStatement()).append("\n");
            } else {
                context.append("Identity: Not set yet.\n");
            }

            // Habits Status (Today)
            List<HabitResponse> habits = habitService.getUserHabits(resolvedEmail);
            long completedCount = habits.stream().filter(HabitResponse::isCompletedToday).count();
            long totalCount = habits.size();
            context.append("HABIT STATUS (Today): ").append(completedCount).append("/").append(totalCount).append(" completed.\n");
            context.append("Uncompleted Habits Today: ");
            habits.stream().filter(h -> !h.isCompletedToday()).forEach(h -> context.append(h.getName()).append(", "));
            context.append("\n");

            // Recent Moods
            List<MoodLog> moods = moodService.getRecentMoods(uId);
            if (!moods.isEmpty()) {
                context.append("RECENT MOODS (Last 24h): ");
                for (MoodLog m : moods) {
                    context.append("[").append(m.getMoodType()).append("] ");
                    if ("GRATITUDE".equals(m.getMoodType()) && m.getNote() != null) {
                        context.append("Gratitude: \"").append(m.getNote()).append("\" ");
                    }
                }
                context.append("\n");
            }
        } catch (Exception e) {
            return "Error retrieving user status: " + e.getMessage();
        }
        return context.toString();
    }

    @Tool(name = "get_user_memory_context", description = "Get the user's saved long-term memory context including stable facts, behavior insights, and daily summaries.")
    public String getUserMemoryContext(
            @ToolParam(name = "email", description = "The user's email address. Optional when user is authenticated.") String email,
            Agent agent) {
        String resolvedEmail = resolveEmail(email, agent);
        if (!StringUtils.hasText(resolvedEmail)) {
            return "No memory context available: user is not authenticated.";
        }
        return memoryService.getMemoryContext(resolvedEmail, 6, 8, 5);
    }

    @Tool(name = "save_user_insight", description = "Save a concise behavioral insight about the user. Use when user reveals preferences, obstacles, motivation, or routines.")
    public String saveUserInsight(
            @ToolParam(name = "email", description = "The user's email address. Optional when user is authenticated.") String email,
            @ToolParam(name = "insight", description = "A concise, reusable user insight in one sentence.") String insight,
            Agent agent) {
        String resolvedEmail = resolveEmail(email, agent);
        if (!StringUtils.hasText(resolvedEmail)) {
            return "Failed to save insight: user is not authenticated.";
        }
        boolean saved = memoryService.saveUserMemory(resolvedEmail, CoachMemory.MemoryType.USER_INSIGHT, insight);
        return saved ? "Saved user insight." : "Skipped saving insight (empty or duplicate).";
    }

    @Tool(name = "save_long_term_fact", description = "Save a durable user fact that is likely stable over time, such as schedule constraints or preferred coaching style.")
    public String saveLongTermFact(
            @ToolParam(name = "email", description = "The user's email address. Optional when user is authenticated.") String email,
            @ToolParam(name = "fact", description = "A durable fact in one sentence.") String fact,
            Agent agent) {
        String resolvedEmail = resolveEmail(email, agent);
        if (!StringUtils.hasText(resolvedEmail)) {
            return "Failed to save long-term fact: user is not authenticated.";
        }
        boolean saved = memoryService.saveUserMemory(resolvedEmail, CoachMemory.MemoryType.LONG_TERM_FACT, fact);
        return saved ? "Saved long-term fact." : "Skipped saving fact (empty or duplicate).";
    }

    @Tool(name = "save_user_identity", description = "Save the user's desired identity statement (e.g., 'I am a runner'). Use this when the user confirms their identity goal.")
    public String saveUserIdentity(
            @ToolParam(name = "email", description = "The user's email address. Optional when user is authenticated.") String email,
            @ToolParam(name = "identity", description = "The identity statement.") String identity,
            Agent agent) {
        String resolvedEmail = resolveEmail(email, agent);
        if (!StringUtils.hasText(resolvedEmail)) {
            return "Failed to save identity: user is not authenticated.";
        }
        userService.updateIdentity(resolvedEmail, identity);
        return "Identity saved: " + identity;
    }

    @Tool(name = "create_first_habit", description = "Create the user's first habit. Use this when the user agrees on a habit.")
    public String createFirstHabit(
            @ToolParam(name = "email", description = "The user's email address. Optional when user is authenticated.") String email,
            @ToolParam(name = "habitName", description = "The name of the habit.") String habitName,
            @ToolParam(name = "twoMinuteVersion", description = "The 2-minute version of the habit.") String twoMinuteVersion,
            Agent agent) {
        String resolvedEmail = resolveEmail(email, agent);
        if (!StringUtils.hasText(resolvedEmail)) {
            return "Failed to create habit: user is not authenticated.";
        }

        HabitRequest request = new HabitRequest();
        request.setName(habitName);
        request.setTwoMinuteVersion(twoMinuteVersion);
        // Defaults for first habit to keep it simple
        request.setCueImplementationIntention("When I wake up");
        request.setCueHabitStack("After my morning coffee");
        
        habitService.createHabit(resolvedEmail, request);
        return "Habit created: " + habitName + " (2-min: " + twoMinuteVersion + ")";
    }

    @Tool(name = "suggest_habits", description = "Suggest atomic habits based on a user's desired identity.")
    public String suggestHabits(
            @ToolParam(name = "identity", description = "The identity the user wants to build (e.g., 'writer', 'athlete').") String identity) {
        return "Suggested habits for " + identity + ":\n" +
               "1. [2-minute rule] Do the activity for just 2 minutes.\n" +
               "2. [Environment] Prepare the environment the night before.\n" +
               "3. [Stacking] Do it after a current daily habit.";
    }

    @Tool(name = "optimize_environment", description = "Suggest environment changes to make a cue obvious.")
    public String optimizeEnvironment(
            @ToolParam(name = "habit", description = "The habit the user wants to build.") String habit) {
        return "To make '" + habit + "' obvious:\n" +
               "- Place relevant items in plain sight.\n" +
               "- Remove distractions from the area.\n" +
               "- Use a visual cue like a sticky note.";
    }

    @Tool(name = "generate_habit_plan", description = "Generate a structured habit plan for a specific goal.")
    public String generateHabitPlan(
            @ToolParam(name = "goal", description = "The goal to achieve (e.g., 'Marathon', 'Learn Spanish').") String goal,
            @ToolParam(name = "duration", description = "The duration of the plan (e.g., '3 months').") String duration) {
        return "Please generate a detailed plan for " + goal + " over " + duration + ". " +
               "Return a JSON list of habits with fields: name, twoMinuteVersion, cueImplementationIntention, cueHabitStack.";
    }
}
