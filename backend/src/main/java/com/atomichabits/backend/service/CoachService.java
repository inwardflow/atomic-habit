package com.atomichabits.backend.service;

import lombok.extern.slf4j.Slf4j;
import com.atomichabits.backend.dto.HabitResponse;
import com.atomichabits.backend.dto.UserProfileResponse;
import com.atomichabits.backend.dto.UserStatsResponse;
import com.atomichabits.backend.dto.WeeklyReviewResponse;
import com.atomichabits.backend.model.ChatMessage;
import com.atomichabits.backend.model.User;
import com.atomichabits.backend.model.WeeklyReview;
import com.atomichabits.backend.repository.WeeklyReviewRepository;
import com.atomichabits.backend.repository.ChatMessageRepository;
import com.atomichabits.backend.repository.UserRepository;
import com.atomichabits.backend.agent.CoachTools;
import com.atomichabits.backend.config.CoachPromptProperties;
import com.atomichabits.backend.dto.HabitResponse;
import com.atomichabits.backend.model.MoodLog;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class CoachService {
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```");
    private static final Pattern SUGGESTION_SENTENCE_PATTERN = Pattern.compile("([^.!?]+[.!?])");

    private final AgentScopeClient agentScopeClient;
    private final CoachTools coachTools;
    private final HabitService habitService;
    private final UserService userService;
    private final MoodService moodService;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final WeeklyReviewRepository weeklyReviewRepository;
    private final CoachPromptProperties promptProperties;
    private final MemoryService memoryService;
    private final ObjectMapper objectMapper;

    public CoachService(AgentScopeClient agentScopeClient, CoachTools coachTools, HabitService habitService, UserService userService, MoodService moodService,
                        ChatMessageRepository chatMessageRepository, UserRepository userRepository,
                        WeeklyReviewRepository weeklyReviewRepository, CoachPromptProperties promptProperties,
                        MemoryService memoryService) {
        this.agentScopeClient = agentScopeClient;
        this.coachTools = coachTools;
        this.habitService = habitService;
        this.userService = userService;
        this.moodService = moodService;
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
        this.weeklyReviewRepository = weeklyReviewRepository;
        this.promptProperties = promptProperties;
        this.memoryService = memoryService;
        this.objectMapper = new ObjectMapper();
    }

    public String chat(String email, String userMessage) {
        // Build context
        String context = buildDailyContext(email, userMessage);

        // Fetch last 20 messages for better context memory
        String history = getFormattedChatHistory(email);
        if (!history.isEmpty()) {
            context += "\nRECENT CONVERSATION HISTORY:\n" + history + "\n";
        }

        // Check for cold-start users (no habits/identity yet).
        boolean isColdStart = isColdStartUser(email);

        String systemPrompt;
        if (isColdStart) {
            systemPrompt = promptProperties.getColdStartSystem();
        } else {
            systemPrompt = promptProperties.getRegularSystem();
        }

        // Save user message
        saveMessage(email, "user", userMessage);

        String aiResponse = agentScopeClient.call((!context.isEmpty() ? "Context:\n" + context + "\nUser Message: " : "") + userMessage, systemPrompt, coachTools);

        // Save AI response
        saveMessage(email, "ai", aiResponse);

        try {
            memoryService.ingestConversationSignals(email, List.of(
                    Msg.builder()
                            .role(MsgRole.USER)
                            .content(TextBlock.builder().text(userMessage).build())
                            .build(),
                    Msg.builder()
                            .role(MsgRole.ASSISTANT)
                            .content(TextBlock.builder().text(aiResponse).build())
                            .build()
            ));
        } catch (Exception ignored) {
            // Memory extraction should not block normal chat responses.
        }

        return aiResponse;
    }

    public String generateGreeting(String email) {
        String context = buildDailyContext(email, null);
        UserProfileResponse profile = userService.getUserProfile(email);
        boolean isColdStart = isColdStartUser(email);

        boolean isNewUser = false;
        if (profile.getCreatedAt() != null) {
            isNewUser = profile.getCreatedAt().isAfter(java.time.LocalDateTime.now().minusDays(1));
        }

        String userPrompt;
        if (isNewUser || isColdStart) {
            userPrompt = promptProperties.getGreetingUserNew();
        } else {
            userPrompt = promptProperties.getGreetingUserExisting();
        }

        String systemPrompt = promptProperties.getGreetingSystem();

        // Only save if we get a valid response (which callAgent handles)
        String aiResponse = agentScopeClient.call("Context:\n" + context + "\n\n" + userPrompt, systemPrompt, coachTools);
        saveMessage(email, "ai", aiResponse);
        return aiResponse;
    }

    private boolean isColdStartUser(String email) {
        try {
            UserProfileResponse profile = userService.getUserProfile(email);
            List<HabitResponse> habits = habitService.getUserHabits(email);
            boolean missingIdentity = profile.getIdentityStatement() == null || profile.getIdentityStatement().isBlank();
            return habits.isEmpty() || missingIdentity;
        } catch (Exception e) {
            // If context cannot be loaded, default to cold-start guidance.
            return true;
        }
    }

    public List<ChatMessage> getChatHistory(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return Collections.emptyList();
        return chatMessageRepository.findByUserIdOrderByTimestampAsc(user.getId());
    }

    private String getFormattedChatHistory(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return "";
        List<ChatMessage> messages = chatMessageRepository.findByUserIdOrderByTimestampDesc(user.getId(), PageRequest.of(0, 20));
        Collections.reverse(messages); // Oldest first

        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : messages) {
            sb.append(msg.getRole().toUpperCase()).append(": ").append(msg.getContent()).append("\n");
        }
        return sb.toString();
    }

    private void saveMessage(String email, String role, String content) {
        userRepository.findByEmail(email).ifPresent(user -> {
            ChatMessage message = ChatMessage.builder()
                    .user(user)
                    .role(role)
                    .content(content)
                    .build();
            chatMessageRepository.save(message);
        });
    }

    private String buildDailyContext(String email, String currentUserMessage) {
        StringBuilder context = new StringBuilder();
        try {
            UserProfileResponse profile = userService.getUserProfile(email);
            Long uId = profile.getId();

            // Inject Habits Status (Today)
            List<HabitResponse> habits = habitService.getUserHabits(email);
            long completedCount = habits.stream().filter(HabitResponse::isCompletedToday).count();
            long totalCount = habits.size();
            context.append("HABIT STATUS (Today): ").append(completedCount).append("/").append(totalCount).append(" completed.\n");
            context.append("Uncompleted Habits Today: ");
            habits.stream().filter(h -> !h.isCompletedToday()).forEach(h -> context.append(h.getName()).append(", "));
            context.append("\n");

            List<MoodLog> moods = moodService.getRecentMoods(uId);
            if (!moods.isEmpty()) {
                context.append("RECENT USER MOOD/LOGS (Last 24h): ");
                for (MoodLog m : moods) {
                    context.append("[").append(m.getMoodType()).append("] ");
                    if ("GRATITUDE".equals(m.getMoodType()) && m.getNote() != null) {
                        context.append("Gratitude: \"").append(m.getNote()).append("\" ");
                    }
                }
                context.append("\nIf mood is OVERWHELMED, be extra gentle and suggest only tiny steps. If GRATITUDE is present, acknowledge it positively.\n");
            }

            if (profile.getIdentityStatement() != null) {
                context.append("Identity: ").append(profile.getIdentityStatement()).append("\n");
            }

            String memoryContext = memoryService.getRelevantMemoryContext(email, currentUserMessage, 8);
            if (StringUtils.hasText(memoryContext) && !memoryContext.startsWith("No saved long-term memory")) {
                context.append("\n").append(memoryContext).append("\n");
            }
        } catch (Exception e) {
            // Ignore if user lookup fails
        }
        return context.toString();
    }

    public String weeklyReview(String email) {
        // Gather user context
        List<HabitResponse> habits = habitService.getUserHabits(email);
        UserStatsResponse stats = userService.getUserStats(email);
        UserProfileResponse profile = userService.getUserProfile(email);

        StringBuilder context = new StringBuilder();
        context.append("User Identity: ").append(profile.getIdentityStatement()).append("\n");
        context.append("Current Streak: ").append(stats.getCurrentStreak()).append(" days\n");
        context.append("Total Habits Completed: ").append(stats.getTotalHabitsCompleted()).append("\n");
        context.append("Habits:\n");
        for (HabitResponse habit : habits) {
            context.append("- ").append(habit.getName())
                   .append(" (Completed Today: ").append(habit.isCompletedToday()).append(")\n");
        }

        try {
             Long uId = profile.getId();
             // Fetch last 7 days of moods for weekly review
             List<MoodLog> moods = moodService.getMoodsSince(uId, java.time.LocalDateTime.now().minusDays(7));
             List<String> gratitude = moods.stream()
                     .filter(m -> "GRATITUDE".equals(m.getMoodType()))
                     .map(MoodLog::getNote)
                     .toList();

             if (!gratitude.isEmpty()) {
                 context.append("\nRecent Gratitude Logs (Last 7 Days):\n");
                 gratitude.forEach(g -> context.append("- \"").append(g).append("\"\n"));
             }
        } catch (Exception e) {
            // ignore
        }

        String userPrompt = promptProperties.getWeeklyReviewUser();

        String systemPrompt = promptProperties.getWeeklyReviewSystem();

        saveMessage(email, "user", "Start Weekly Review");
        String aiResponse = agentScopeClient.call("Context:\n" + context + "\n\n" + userPrompt, systemPrompt, true);
        saveMessage(email, "ai", aiResponse);

        saveWeeklyReviewRecord(email, stats, aiResponse);
        return aiResponse;
    }

    public List<WeeklyReviewResponse> getRecentWeeklyReviews(String email, int limit) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return Collections.emptyList();

        int safeLimit = Math.max(1, Math.min(limit, 50));
        return weeklyReviewRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, safeLimit))
                .stream()
                .map(this::mapToWeeklyReviewResponse)
                .collect(Collectors.toList());
    }

    private void saveWeeklyReviewRecord(String email, UserStatsResponse stats, String aiResponse) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) return;

        WeeklyReviewPayload payload = extractWeeklyReviewPayload(aiResponse, stats);
        WeeklyReview review = WeeklyReview.builder()
                .user(userOpt.get())
                .totalCompleted(payload.totalCompleted())
                .currentStreak(payload.currentStreak())
                .bestStreak(payload.bestStreak())
                .highlightsJson(toJson(payload.highlights()))
                .suggestion(payload.suggestion())
                .rawResponse(aiResponse)
                .build();

        weeklyReviewRepository.save(review);
    }

    private WeeklyReviewResponse mapToWeeklyReviewResponse(WeeklyReview review) {
        return WeeklyReviewResponse.builder()
                .id(review.getId())
                .totalCompleted(review.getTotalCompleted())
                .currentStreak(review.getCurrentStreak())
                .bestStreak(review.getBestStreak())
                .highlights(parseHighlights(review.getHighlightsJson()))
                .suggestion(review.getSuggestion())
                .createdAt(review.getCreatedAt())
                .formattedDate(review.getCreatedAt() != null
                        ? review.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM dd"))
                        : "")
                .build();
    }

    private WeeklyReviewPayload extractWeeklyReviewPayload(String aiResponse, UserStatsResponse stats) {
        WeeklyReviewPayload fallback = defaultWeeklyReviewPayload(stats, aiResponse);
        if (aiResponse == null || aiResponse.isBlank()) {
            return fallback;
        }

        Matcher matcher = CODE_BLOCK_PATTERN.matcher(aiResponse);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            try {
                JsonNode node = objectMapper.readTree(candidate);
                WeeklyReviewPayload parsed = parsePayloadFromJson(node, fallback);
                if (parsed != null) {
                    return parsed;
                }
            } catch (Exception ignored) {
                // Ignore malformed JSON blocks and continue with next candidate.
            }
        }

        return fallback;
    }

    private WeeklyReviewPayload parsePayloadFromJson(JsonNode node, WeeklyReviewPayload fallback) {
        if (node == null || node.isNull()) return null;

        // Tool call style: { "name": "present_weekly_review", "arguments": { ... } }
        if (node.has("name") && "present_weekly_review".equals(node.path("name").asText()) && node.has("arguments")) {
            JsonNode args = node.path("arguments");
            return fromStatsNode(args, args.path("highlights"), args.path("suggestion"), fallback);
        }

        // Direct style: { "stats": {...}, "highlights": [...], "suggestion": "..." }
        if (node.has("stats")) {
            return fromStatsNode(node.path("stats"), node.path("highlights"), node.path("suggestion"), fallback);
        }

        // Flat style: { "totalCompleted": 8, "currentStreak": 4, ... }
        if (node.has("totalCompleted") || node.has("currentStreak")) {
            return fromStatsNode(node, node.path("highlights"), node.path("suggestion"), fallback);
        }

        return null;
    }

    private WeeklyReviewPayload fromStatsNode(JsonNode statsNode, JsonNode highlightsNode, JsonNode suggestionNode, WeeklyReviewPayload fallback) {
        int totalCompleted = statsNode.path("totalCompleted").isNumber()
                ? statsNode.path("totalCompleted").asInt()
                : fallback.totalCompleted();
        int currentStreak = statsNode.path("currentStreak").isNumber()
                ? statsNode.path("currentStreak").asInt()
                : fallback.currentStreak();
        int bestStreak = statsNode.path("bestStreak").isNumber()
                ? statsNode.path("bestStreak").asInt()
                : fallback.bestStreak();

        List<String> highlights = fallback.highlights();
        if (highlightsNode != null && highlightsNode.isArray()) {
            List<String> parsedHighlights = new ArrayList<>();
            for (JsonNode item : highlightsNode) {
                if (item.isTextual()) {
                    String text = item.asText().trim();
                    if (!text.isEmpty()) parsedHighlights.add(text);
                }
            }
            if (!parsedHighlights.isEmpty()) {
                highlights = parsedHighlights;
            }
        }

        String suggestion = fallback.suggestion();
        if (suggestionNode != null && suggestionNode.isTextual()) {
            String parsedSuggestion = suggestionNode.asText().trim();
            if (!parsedSuggestion.isEmpty()) {
                suggestion = parsedSuggestion;
            }
        }

        return new WeeklyReviewPayload(totalCompleted, currentStreak, bestStreak, highlights, suggestion);
    }

    private WeeklyReviewPayload defaultWeeklyReviewPayload(UserStatsResponse stats, String aiResponse) {
        int totalCompleted = stats.getTotalHabitsCompleted();
        int currentStreak = stats.getCurrentStreak();
        int bestStreak = stats.getLongestStreak();

        List<String> highlights = new ArrayList<>();
        if (totalCompleted > 0) {
            highlights.add("You completed " + totalCompleted + " habits in total. Great consistency momentum.");
        } else {
            highlights.add("You showed up for review this week. That is a meaningful identity vote.");
        }
        if (currentStreak > 0) {
            highlights.add("Current streak: " + currentStreak + " days.");
        }

        String suggestion = "Keep it tiny and consistent this week: one easy action you can repeat daily.";
        if (aiResponse != null) {
            Matcher matcher = SUGGESTION_SENTENCE_PATTERN.matcher(aiResponse);
            if (matcher.find()) {
                String sentence = matcher.group(1).trim();
                if (!sentence.isEmpty()) suggestion = sentence;
            }
        }

        return new WeeklyReviewPayload(totalCompleted, currentStreak, bestStreak, highlights, suggestion);
    }

    private String toJson(List<String> highlights) {
        try {
            return objectMapper.writeValueAsString(highlights == null ? Collections.emptyList() : highlights);
        } catch (Exception ignored) {
            return "[]";
        }
    }

    private List<String> parseHighlights(String highlightsJson) {
        try {
            if (highlightsJson == null || highlightsJson.isBlank()) return Collections.emptyList();
            return objectMapper.readValue(highlightsJson, new TypeReference<>() {});
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private record WeeklyReviewPayload(int totalCompleted, int currentStreak, int bestStreak,
                                       List<String> highlights, String suggestion) {
    }

    public String generateReminder(String email, String habitName, int currentStreak) {
        StringBuilder context = new StringBuilder();
        try {
            UserProfileResponse profile = userService.getUserProfile(email);
            if (profile.getIdentityStatement() != null) {
                context.append("User Identity: ").append(profile.getIdentityStatement()).append("\n");
            }
            List<MoodLog> moods = moodService.getRecentMoods(profile.getId());
            if (!moods.isEmpty()) {
                context.append("Recent Mood: ").append(moods.get(0).getMoodType());
                if (moods.get(0).getNote() != null) {
                    context.append(" (").append(moods.get(0).getNote()).append(")");
                }
                context.append("\n");
            }
        } catch (Exception e) {
            // ignore
        }

        context.append("Target Habit: ").append(habitName).append("\n");
        context.append("Current Streak: ").append(currentStreak).append(" days\n");

        String userPrompt = promptProperties.getReminderUser();
        String systemPrompt = promptProperties.getReminderSystem();

        // Use a shorter fallback if system prompt is missing (though it shouldn't be)
        if (systemPrompt == null) systemPrompt = "You are a helpful habit coach. Send a short reminder.";

        return agentScopeClient.call("Context:\n" + context + "\n\n" + userPrompt, systemPrompt, coachTools);
    }
}
