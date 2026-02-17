package com.atomichabits.backend.service;

import com.atomichabits.backend.model.*;
import com.atomichabits.backend.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.OpenAIChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class MemoryService {
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE);
    private static final int MAX_SAVED_PER_TURN = 3;

    private static final Set<String> MEMORY_QUERY_STOPWORDS = Set.of(
            "the", "and", "for", "with", "that", "this", "have", "just",
            "what", "when", "your", "about", "from", "want", "need", "today",
            "you", "are", "was", "were", "can", "could", "should", "would"
    );

    private static final Set<String> STABLE_FACT_HINTS = Set.of(
            "prefer", "usually", "always", "never", "schedule", "work", "morning",
            "evening", "weekend", "commute", "shift", "cannot", "can't", "allergic",
            "injury", "adhd", "sleep", "routine", "at home", "at office"
    );

    private static final Set<String> INSIGHT_HINTS = Set.of(
            "struggle", "difficult", "hard to", "overwhelmed", "distract",
            "procrastinat", "forget", "motivation", "trigger", "tempt", "stuck"
    );

    private static final Set<String> SHORT_TERM_MARKERS = Set.of(
            "today", "yesterday", "tomorrow", "right now", "this afternoon", "this evening"
    );

    private final CoachMemoryRepository memoryRepository;
    private final UserRepository userRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MoodService moodService;
    private final HabitCompletionRepository habitCompletionRepository;

    @Value("${agentscope.model.api-key}")
    private String apiKey;

    @Value("${agentscope.model.model-name}")
    private String modelName;

    @Value("${coach.memory.llm-extraction-enabled:true}")
    private boolean llmExtractionEnabled;

    private final ObjectMapper objectMapper;

    public MemoryService(CoachMemoryRepository memoryRepository, UserRepository userRepository, ChatMessageRepository chatMessageRepository, MoodService moodService, HabitCompletionRepository habitCompletionRepository) {
        this.memoryRepository = memoryRepository;
        this.userRepository = userRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.moodService = moodService;
        this.habitCompletionRepository = habitCompletionRepository;
        this.objectMapper = new ObjectMapper();
    }

    // Run at 2 AM every day to summarize yesterday
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void generateDailySummaries() {
        List<User> users = userRepository.findAll();
        LocalDate yesterday = LocalDate.now().minusDays(1);
        
        for (User user : users) {
            try {
                generateSummaryForDate(user, yesterday);
            } catch (Exception e) {
                System.err.println("Failed to generate summary for user " + user.getEmail() + ": " + e.getMessage());
            }
        }
    }
    
    @Transactional
    public void generateSummaryForDate(User user, LocalDate date) {
        // Check if exists
        if (memoryRepository.findByUserIdAndReferenceDateAndType(user.getId(), date, CoachMemory.MemoryType.DAILY_SUMMARY).isPresent()) {
            return;
        }

        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(LocalTime.MAX);

        // Gather Data
        // 1. Chats
        List<ChatMessage> chats = chatMessageRepository.findByUserIdAndTimestampBetweenOrderByTimestampAsc(user.getId(), start, end);
        
        // 2. Moods
        // Filter moods for that day
        List<MoodLog> moods = moodService.getMoodsSince(user.getId(), start).stream()
                .filter(m -> m.getCreatedAt().isBefore(end))
                .collect(Collectors.toList());
        
        // 3. Habits
        List<HabitCompletion> completions = habitCompletionRepository.findByHabitUserIdAndCompletedAtBetween(user.getId(), start, end);
        
        // If no activity, skip
        if (chats.isEmpty() && moods.isEmpty() && completions.isEmpty()) {
            return; 
        }
        
        String context = buildContextForSummary(chats, moods, completions);
        
        String prompt = "Please create a concise daily summary (max 50 words) for the user's activity on " + date + ". " +
                        "Include key achievements (habits completed), mood patterns, and any important topics discussed in chat. " +
                        "This summary will be used as long-term memory for future coaching. " +
                        "\n\nContext:\n" + context;
                        
        String summary = callAI(prompt);
        
        if (summary != null && !summary.isEmpty()) {
            CoachMemory memory = CoachMemory.builder()
                    .user(user)
                    .type(CoachMemory.MemoryType.DAILY_SUMMARY)
                    .referenceDate(date)
                    .content(summary)
                    .importanceScore(2)
                    .expiresAt(date.plusDays(35))
                    .build();
                    
            memoryRepository.save(memory);
        }
    }
    
    private String buildContextForSummary(List<ChatMessage> chats, List<MoodLog> moods, List<HabitCompletion> completions) {
        StringBuilder sb = new StringBuilder();
        
        if (!completions.isEmpty()) {
            sb.append("Habits Completed:\n");
            for (HabitCompletion hc : completions) {
                sb.append("- ").append(hc.getHabit().getName()).append("\n");
            }
        }
        
        if (!moods.isEmpty()) {
            sb.append("Moods Logged:\n");
            for (MoodLog m : moods) {
                sb.append("- ").append(m.getMoodType());
                if (m.getNote() != null) sb.append(": ").append(m.getNote());
                sb.append("\n");
            }
        }
        
        if (!chats.isEmpty()) {
            sb.append("Chat History:\n");
            for (ChatMessage msg : chats) {
                sb.append(msg.getRole().toUpperCase()).append(": ").append(msg.getContent()).append("\n");
            }
        }
        
        return sb.toString();
    }

    // Protected for testing
    protected String callAI(String prompt) {
        return callAIWithSystemPrompt(prompt,
                "Summarize user activity clearly and compassionately.",
                "MemorySummarizer");
    }

    // Protected for testing
    protected String callAIForSignalExtraction(String prompt) {
        return callAIWithSystemPrompt(prompt,
                "Extract durable user profile memory for habit coaching. Return strict JSON only.",
                "MemorySignalExtractor");
    }

    private String callAIWithSystemPrompt(String prompt, String systemPrompt, String agentName) {
        try {
            OpenAIChatModel model = OpenAIChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(modelName)
                    .baseUrl("https://api.siliconflow.com/v1")
                    .build();

            ReActAgent summarizer = ReActAgent.builder()
                    .name(agentName)
                    .sysPrompt(systemPrompt)
                    .model(model)
                    .build();

            Msg response = summarizer.call(Msg.builder()
                    .role(MsgRole.USER)
                    .content(TextBlock.builder().text(prompt).build())
                    .build())
                    .block();

            return response != null ? response.getTextContent() : null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Transactional(readOnly = true)
    public List<CoachMemory> getRecentMemories(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return Collections.emptyList();

        // Return recent non-expired memories.
        return memoryRepository.findTop30ByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(this::isActiveMemory)
                .collect(Collectors.toList());
    }

    @Transactional
    public boolean saveUserInsight(String email, String insight) {
        return saveUserMemory(email, CoachMemory.MemoryType.USER_INSIGHT, insight);
    }

    @Transactional
    public boolean saveLongTermFact(String email, String fact) {
        return saveUserMemory(email, CoachMemory.MemoryType.LONG_TERM_FACT, fact);
    }

    @Transactional
    public boolean saveUserMemory(String email, CoachMemory.MemoryType type, String content) {
        if (!StringUtils.hasText(email) || type == null || !StringUtils.hasText(content)) {
            return false;
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) return false;

        String normalizedCandidate = normalizeForDedup(content);
        if (normalizedCandidate.length() < 8) {
            return false;
        }

        List<CoachMemory> recentSameType = memoryRepository
                .findTop20ByUserIdAndTypeOrderByCreatedAtDesc(userOpt.get().getId(), type);

        boolean duplicate = recentSameType.stream()
                .filter(this::isActiveMemory)
                .map(CoachMemory::getContent)
                .filter(StringUtils::hasText)
                .map(this::normalizeForDedup)
                .anyMatch(existing -> isNearDuplicate(existing, normalizedCandidate));

        if (duplicate) {
            return false;
        }

        int importanceScore = computeImportanceScore(type, content);
        LocalDate expiresAt = calculateExpiryDate(type, importanceScore);

        CoachMemory memory = CoachMemory.builder()
                .user(userOpt.get())
                .type(type)
                .content(content.trim())
                // Keep timeline display consistent for all memory types.
                .referenceDate(LocalDate.now())
                .importanceScore(importanceScore)
                .expiresAt(expiresAt)
                .build();

        memoryRepository.save(memory);
        return true;
    }

    @Transactional(readOnly = true)
    public String getMemoryContext(String email, int factLimit, int insightLimit, int summaryLimit) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return "No saved long-term memory found for this user.";
        }

        int safeFactLimit = Math.max(1, Math.min(factLimit, 10));
        int safeInsightLimit = Math.max(1, Math.min(insightLimit, 10));
        int safeSummaryLimit = Math.max(1, Math.min(summaryLimit, 10));

        List<CoachMemory> facts = memoryRepository
                .findTop10ByUserIdAndTypeOrderByCreatedAtDesc(user.getId(), CoachMemory.MemoryType.LONG_TERM_FACT)
                .stream()
                .filter(this::isActiveMemory)
                .sorted(memoryPriorityComparator())
                .limit(safeFactLimit)
                .collect(Collectors.toList());

        List<CoachMemory> insights = memoryRepository
                .findTop10ByUserIdAndTypeOrderByCreatedAtDesc(user.getId(), CoachMemory.MemoryType.USER_INSIGHT)
                .stream()
                .filter(this::isActiveMemory)
                .sorted(memoryPriorityComparator())
                .limit(safeInsightLimit)
                .collect(Collectors.toList());

        List<CoachMemory> summaries = memoryRepository
                .findTop10ByUserIdAndTypeOrderByReferenceDateDesc(user.getId(), CoachMemory.MemoryType.DAILY_SUMMARY)
                .stream()
                .filter(this::isActiveMemory)
                .limit(safeSummaryLimit)
                .sorted(Comparator.comparing(CoachMemory::getReferenceDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        if (facts.isEmpty() && insights.isEmpty() && summaries.isEmpty()) {
            return "No saved long-term memory yet. Build memory from this conversation.";
        }

        StringBuilder sb = new StringBuilder("LONG-TERM USER MEMORY:\n");
        List<CoachMemory> priorityMemories = java.util.stream.Stream.concat(facts.stream(), insights.stream())
                .filter(m -> safeScore(m) >= 4)
                .sorted(memoryPriorityComparator())
                .limit(3)
                .collect(Collectors.toList());

        if (!priorityMemories.isEmpty()) {
            sb.append("Priority coaching preferences:\n");
            for (CoachMemory memory : priorityMemories) {
                sb.append("- ").append(memory.getContent()).append("\n");
            }
        }
        if (!facts.isEmpty()) {
            sb.append("Stable facts:\n");
            for (CoachMemory fact : facts) {
                sb.append("- ").append(fact.getContent()).append(" (P").append(safeScore(fact)).append(")\n");
            }
        }
        if (!insights.isEmpty()) {
            sb.append("Behavioral insights:\n");
            for (CoachMemory insight : insights) {
                sb.append("- ").append(insight.getContent()).append(" (P").append(safeScore(insight)).append(")\n");
            }
        }
        if (!summaries.isEmpty()) {
            sb.append("Recent day summaries:\n");
            for (CoachMemory summary : summaries) {
                String date = summary.getReferenceDate() != null ? summary.getReferenceDate().toString() : "unknown-date";
                sb.append("- [").append(date).append("] ").append(summary.getContent()).append("\n");
            }
        }
        return sb.toString().trim();
    }

    @Transactional(readOnly = true)
    public String getRelevantMemoryContext(String email, String query, int limit) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return "No saved long-term memory found for this user.";
        }

        List<CoachMemory> activeMemories = memoryRepository.findTop30ByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(this::isActiveMemory)
                .collect(Collectors.toList());

        if (activeMemories.isEmpty()) {
            return "No saved long-term memory yet. Build memory from this conversation.";
        }

        int safeLimit = Math.max(1, Math.min(limit, 12));
        Set<String> queryTokens = extractQueryTokens(query);

        List<CoachMemory> profileMemories = activeMemories.stream()
                .filter(m -> m.getType() == CoachMemory.MemoryType.USER_INSIGHT || m.getType() == CoachMemory.MemoryType.LONG_TERM_FACT)
                .sorted(Comparator
                        .comparingInt((CoachMemory m) -> relevanceScore(m, queryTokens))
                        .reversed()
                        .thenComparing(CoachMemory::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(safeLimit)
                .collect(Collectors.toList());

        List<CoachMemory> summaries = activeMemories.stream()
                .filter(m -> m.getType() == CoachMemory.MemoryType.DAILY_SUMMARY)
                .sorted(Comparator.comparing(CoachMemory::getReferenceDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(3)
                .collect(Collectors.toList());

        if (profileMemories.isEmpty() && summaries.isEmpty()) {
            return "No saved long-term memory yet. Build memory from this conversation.";
        }

        StringBuilder sb = new StringBuilder("LONG-TERM USER MEMORY (retrieved for current turn):\n");
        if (!profileMemories.isEmpty()) {
            sb.append("Most relevant profile signals:\n");
            for (CoachMemory memory : profileMemories) {
                sb.append("- ").append(memory.getContent()).append(" (P").append(safeScore(memory)).append(")\n");
            }
        }
        if (!summaries.isEmpty()) {
            sb.append("Recent trajectory snapshots:\n");
            for (CoachMemory summary : summaries) {
                String date = summary.getReferenceDate() != null ? summary.getReferenceDate().toString() : "unknown-date";
                sb.append("- [").append(date).append("] ").append(summary.getContent()).append("\n");
            }
        }
        return sb.toString().trim();
    }

    @Transactional
    public int ingestConversationSignals(String email, List<Msg> messages) {
        if (!StringUtils.hasText(email) || messages == null || messages.isEmpty()) {
            return 0;
        }

        List<String> recentUserMessages = extractRecentUserMessages(messages, 3);
        if (recentUserMessages.isEmpty()) {
            return 0;
        }

        List<MemoryCandidate> candidates = new ArrayList<>(extractCandidatesWithLlm(recentUserMessages));
        if (candidates.size() < 6) {
            for (String userText : recentUserMessages) {
                candidates.addAll(extractMemoryCandidates(userText));
                if (candidates.size() >= 8) {
                    break;
                }
            }
        }

        if (candidates.isEmpty()) {
            return 0;
        }

        int saved = 0;
        Set<String> seen = new LinkedHashSet<>();
        for (MemoryCandidate candidate : candidates) {
            String dedupKey = candidate.type().name() + "|" + normalizeForDedup(candidate.content());
            if (!seen.add(dedupKey)) {
                continue;
            }

            if (saveUserMemory(email, candidate.type(), candidate.content())) {
                saved++;
            }
            if (saved >= MAX_SAVED_PER_TURN) {
                break;
            }
        }
        return saved;
    }

    private List<MemoryCandidate> extractCandidatesWithLlm(List<String> recentUserMessages) {
        if (!llmExtractionEnabled || recentUserMessages == null || recentUserMessages.isEmpty()) {
            return Collections.emptyList();
        }

        String response = callAIForSignalExtraction(buildSignalExtractionPrompt(recentUserMessages));
        return parseStructuredMemoryCandidates(response);
    }

    private String buildSignalExtractionPrompt(List<String> recentUserMessages) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are extracting long-term coaching memory from user messages.\n");
        sb.append("Return JSON only, with this exact shape:\n");
        sb.append("{\"facts\": [\"...\"], \"insights\": [\"...\"]}\n");
        sb.append("Rules:\n");
        sb.append("- facts: stable constraints/preferences/schedules likely useful for weeks.\n");
        sb.append("- insights: recurring behavior patterns, obstacles, motivation triggers.\n");
        sb.append("- Keep each item one concise sentence, <= 140 chars.\n");
        sb.append("- Do not include temporary details tied only to today/yesterday.\n");
        sb.append("- Max 2 facts and 2 insights.\n\n");
        sb.append("Messages:\n");
        for (int i = 0; i < recentUserMessages.size(); i++) {
            sb.append(i + 1).append(". ").append(recentUserMessages.get(i)).append("\n");
        }
        return sb.toString();
    }

    private List<MemoryCandidate> parseStructuredMemoryCandidates(String rawOutput) {
        if (!StringUtils.hasText(rawOutput)) {
            return Collections.emptyList();
        }

        String jsonPayload = extractJsonPayload(rawOutput);
        if (!StringUtils.hasText(jsonPayload)) {
            return Collections.emptyList();
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload);
            List<MemoryCandidate> candidates = new ArrayList<>();
            appendCandidatesFromArray(candidates, root.path("facts"), CoachMemory.MemoryType.LONG_TERM_FACT, 2);
            appendCandidatesFromArray(candidates, root.path("insights"), CoachMemory.MemoryType.USER_INSIGHT, 2);
            return candidates;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private void appendCandidatesFromArray(List<MemoryCandidate> target, JsonNode arrayNode, CoachMemory.MemoryType type, int maxItems) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return;
        }

        int added = 0;
        for (JsonNode item : arrayNode) {
            if (!item.isTextual()) {
                continue;
            }
            String text = item.asText().trim();
            if (!StringUtils.hasText(text)) {
                continue;
            }
            target.add(new MemoryCandidate(type, normalizeCandidateSentence(text)));
            added++;
            if (added >= maxItems) {
                break;
            }
        }
    }

    private String extractJsonPayload(String rawOutput) {
        String trimmed = rawOutput.trim();

        Matcher matcher = JSON_BLOCK_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1).trim();
        }

        return trimmed;
    }

    private String normalizeForDedup(String content) {
        return content.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean isNearDuplicate(String existing, String candidate) {
        if (existing.equals(candidate)) return true;
        if (existing.length() >= 20 && candidate.contains(existing)) return true;
        if (candidate.length() >= 20 && existing.contains(candidate)) return true;
        return false;
    }

    private Comparator<CoachMemory> memoryPriorityComparator() {
        return Comparator
                .comparingInt(this::safeScore)
                .reversed()
                .thenComparing(CoachMemory::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private int safeScore(CoachMemory memory) {
        return memory.getImportanceScore() == null ? 3 : memory.getImportanceScore();
    }

    private boolean isActiveMemory(CoachMemory memory) {
        return memory.getExpiresAt() == null || !memory.getExpiresAt().isBefore(LocalDate.now());
    }

    private int computeImportanceScore(CoachMemory.MemoryType type, String rawContent) {
        String content = rawContent == null ? "" : rawContent.toLowerCase(Locale.ROOT);
        int score = 3;

        if (type == CoachMemory.MemoryType.LONG_TERM_FACT) {
            score += 1;
        }

        if (containsAny(content, "prefer", "usually", "always", "can't", "cannot", "schedule", "work", "morning", "evening")) {
            score += 1;
        }

        if (containsAny(content, "today", "yesterday", "this week", "sometimes", "maybe")) {
            score -= 1;
        }

        if (content.length() > 180) {
            score -= 1;
        }

        if (score < 1) return 1;
        if (score > 5) return 5;
        return score;
    }

    private LocalDate calculateExpiryDate(CoachMemory.MemoryType type, int importanceScore) {
        if (type == CoachMemory.MemoryType.LONG_TERM_FACT) {
            return null;
        }
        if (type == CoachMemory.MemoryType.DAILY_SUMMARY) {
            return LocalDate.now().plusDays(35);
        }

        // USER_INSIGHT: stronger insights live longer.
        if (importanceScore >= 4) {
            return LocalDate.now().plusDays(180);
        }
        return LocalDate.now().plusDays(90);
    }

    private Set<String> extractQueryTokens(String query) {
        if (!StringUtils.hasText(query)) {
            return Collections.emptySet();
        }

        return java.util.Arrays.stream(query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .map(String::trim)
                .filter(token -> token.length() >= 3)
                .filter(token -> !MEMORY_QUERY_STOPWORDS.contains(token))
                .limit(12)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private int relevanceScore(CoachMemory memory, Set<String> queryTokens) {
        int score = safeScore(memory) * 2;

        if (memory.getType() == CoachMemory.MemoryType.LONG_TERM_FACT) {
            score += 2;
        } else if (memory.getType() == CoachMemory.MemoryType.USER_INSIGHT) {
            score += 1;
        }

        if (!queryTokens.isEmpty() && StringUtils.hasText(memory.getContent())) {
            String normalizedContent = " " + normalizeForDedup(memory.getContent()) + " ";
            int hits = 0;
            for (String token : queryTokens) {
                if (normalizedContent.contains(" " + token + " ")) {
                    hits++;
                }
            }
            score += Math.min(hits * 2, 8);
        }

        return score;
    }

    private List<String> extractRecentUserMessages(List<Msg> messages, int limit) {
        List<String> collected = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0 && collected.size() < limit; i--) {
            Msg msg = messages.get(i);
            if (msg == null || msg.getRole() != MsgRole.USER) {
                continue;
            }

            String text = msg.getTextContent();
            if (StringUtils.hasText(text)) {
                collected.add(text.trim());
            }
        }
        Collections.reverse(collected);
        return collected;
    }

    private List<MemoryCandidate> extractMemoryCandidates(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return Collections.emptyList();
        }

        String normalized = rawText.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }

        String[] sentences = normalized.split("(?<=[.!?\\u3002\\uFF01\\uFF1F])\\s+");
        if (sentences.length == 0) {
            sentences = new String[]{normalized};
        }

        List<MemoryCandidate> candidates = new ArrayList<>();
        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (trimmed.length() < 18 || trimmed.length() > 220) {
                continue;
            }

            String lower = " " + trimmed.toLowerCase(Locale.ROOT) + " ";
            if (lower.contains("?") || lower.contains("\uFF1F")) {
                continue;
            }

            boolean firstPerson = containsAny(lower, " i ", " i'm ", " i am ", " my ", " me ", " myself ");
            if (!firstPerson) {
                continue;
            }

            boolean hasStableFactHint = STABLE_FACT_HINTS.stream().anyMatch(lower::contains);
            boolean hasInsightHint = INSIGHT_HINTS.stream().anyMatch(lower::contains);
            boolean shortTermOnly = SHORT_TERM_MARKERS.stream().anyMatch(lower::contains) && !hasStableFactHint;

            if (shortTermOnly || (!hasStableFactHint && !hasInsightHint)) {
                continue;
            }

            CoachMemory.MemoryType type = hasStableFactHint
                    ? CoachMemory.MemoryType.LONG_TERM_FACT
                    : CoachMemory.MemoryType.USER_INSIGHT;

            candidates.add(new MemoryCandidate(type, normalizeCandidateSentence(trimmed)));
            if (candidates.size() >= 2) {
                break;
            }
        }

        return candidates;
    }

    private String normalizeCandidateSentence(String sentence) {
        String normalized = sentence
                .replaceAll("^[\"'\\u201C\\u201D\\u2018\\u2019]+", "")
                .replaceAll("[\"'\\u201C\\u201D\\u2018\\u2019]+$", "")
                .trim();

        if (normalized.length() > 220) {
            normalized = normalized.substring(0, 220).trim();
        }

        if (!normalized.endsWith(".") && !normalized.endsWith("!") && !normalized.endsWith("?")
                && !normalized.endsWith("\u3002") && !normalized.endsWith("\uFF01") && !normalized.endsWith("\uFF1F")) {
            normalized = normalized + ".";
        }
        return normalized;
    }

    private boolean containsAny(String content, String... keywords) {
        for (String keyword : keywords) {
            if (content.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private record MemoryCandidate(CoachMemory.MemoryType type, String content) {
    }
}
