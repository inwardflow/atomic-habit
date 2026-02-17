package com.atomichabits.backend.agent;

import com.atomichabits.backend.model.User;
import com.atomichabits.backend.repository.UserRepository;
import com.atomichabits.backend.service.CoachTurnMemoryHitService;
import com.atomichabits.backend.service.MemoryService;
import io.agentscope.core.memory.LongTermMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Component
public class CoachLongTermMemory implements LongTermMemory {
    private static final Logger log = LoggerFactory.getLogger(CoachLongTermMemory.class);
    private static final String USER_THREAD_PREFIX = "user-";

    private final MemoryService memoryService;
    private final CoachTurnMemoryHitService coachTurnMemoryHitService;
    private final UserRepository userRepository;

    public CoachLongTermMemory(MemoryService memoryService,
                               CoachTurnMemoryHitService coachTurnMemoryHitService,
                               UserRepository userRepository) {
        this.memoryService = memoryService;
        this.coachTurnMemoryHitService = coachTurnMemoryHitService;
        this.userRepository = userRepository;
    }

    @Override
    public Mono<Void> record(List<Msg> messages) {
        return Mono.<Void>fromRunnable(() -> {
                    String email = resolveEmail(null, messages);
                    if (!StringUtils.hasText(email) || messages == null || messages.isEmpty()) {
                        return;
                    }
                    memoryService.ingestConversationSignals(email, messages);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(error -> {
                    log.warn("Failed to record long-term memory: {}", error.getMessage());
                    return Mono.<Void>empty();
                });
    }

    @Override
    public Mono<String> retrieve(Msg msg) {
        return Mono.fromSupplier(() -> {
                    String email = resolveEmail(msg, null);
                    if (!StringUtils.hasText(email)) {
                        return "";
                    }
                    String query = msg != null ? msg.getTextContent() : "";
                    String context = memoryService.getRelevantMemoryContext(email, query, 8);
                    coachTurnMemoryHitService.updateHits(email, extractMemoryHits(context));
                    return context;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(error -> {
                    log.warn("Failed to retrieve long-term memory: {}", error.getMessage());
                    return Mono.just("");
                });
    }

    private String resolveEmail(Msg msg, List<Msg> messages) {
        String fromAuth = resolveEmailFromAuthentication();
        if (StringUtils.hasText(fromAuth)) {
            return fromAuth;
        }

        Msg probe = msg != null ? msg : findLastUserMessage(messages);
        if (probe == null) {
            return null;
        }
        return resolveEmailFromMetadata(probe.getMetadata());
    }

    private String resolveEmailFromAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        String principal = authentication.getName();
        if (!StringUtils.hasText(principal) || "anonymousUser".equalsIgnoreCase(principal)) {
            return null;
        }
        return principal;
    }

    private Msg findLastUserMessage(List<Msg> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }

        for (int i = messages.size() - 1; i >= 0; i--) {
            Msg msg = messages.get(i);
            if (msg != null && msg.getRole() == MsgRole.USER) {
                return msg;
            }
        }
        return messages.get(messages.size() - 1);
    }

    private String resolveEmailFromMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }

        String fromEmail = valueAsString(metadata.get("email"));
        if (!StringUtils.hasText(fromEmail)) {
            fromEmail = valueAsString(metadata.get("userEmail"));
        }
        if (StringUtils.hasText(fromEmail)) {
            return fromEmail;
        }

        Long userId = null;
        Object userIdValue = metadata.get("userId");
        if (userIdValue == null) {
            userIdValue = metadata.get("uid");
        }
        if (userIdValue instanceof Number number) {
            userId = number.longValue();
        } else if (userIdValue != null) {
            try {
                userId = Long.parseLong(userIdValue.toString().trim());
            } catch (Exception ignored) {
                // ignore malformed user id
            }
        }
        if (userId != null) {
            return userRepository.findById(userId).map(User::getEmail).orElse(null);
        }

        String threadId = valueAsString(metadata.get("threadId"));
        if (!StringUtils.hasText(threadId)) {
            threadId = valueAsString(metadata.get("conversationId"));
        }
        if (StringUtils.hasText(threadId) && threadId.startsWith(USER_THREAD_PREFIX)) {
            try {
                long parsedId = Long.parseLong(threadId.substring(USER_THREAD_PREFIX.length()));
                return userRepository.findById(parsedId).map(User::getEmail).orElse(null);
            } catch (Exception ignored) {
                // ignore malformed thread id
            }
        }

        return null;
    }

    private String valueAsString(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private List<String> extractMemoryHits(String context) {
        if (!StringUtils.hasText(context) || context.startsWith("No saved long-term memory")) {
            return List.of();
        }

        String[] lines = context.split("\\R");
        List<String> hits = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("- ")) {
                continue;
            }

            String hit = trimmed.substring(2)
                    .replaceAll("\\s*\\(P\\d+\\)$", "")
                    .trim();
            if (!hit.isEmpty()) {
                hits.add(hit);
            }
            if (hits.size() >= 6) {
                break;
            }
        }
        return hits;
    }
}
