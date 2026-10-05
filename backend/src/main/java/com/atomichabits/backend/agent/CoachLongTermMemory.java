package com.atomichabits.backend.agent;

import com.atomichabits.backend.model.ChatMessage;
import com.atomichabits.backend.repository.ChatMessageRepository;
import com.atomichabits.backend.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
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
import java.util.ArrayList;

@Component
public class CoachLongTermMemory implements LongTermMemory {
    private static final Logger log = LoggerFactory.getLogger(CoachLongTermMemory.class);

    private final MemoryService memoryService;
    private final CoachTurnMemoryHitService coachTurnMemoryHitService;
    private final UserRepository userRepository;
    private final ChatMessageRepository chatMessageRepository;

    public CoachLongTermMemory(MemoryService memoryService,
                               CoachTurnMemoryHitService coachTurnMemoryHitService,
                               UserRepository userRepository,
                               ChatMessageRepository chatMessageRepository) {
        this.memoryService = memoryService;
        this.coachTurnMemoryHitService = coachTurnMemoryHitService;
        this.userRepository = userRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    /**
     * Returns a view of this memory bound to one user. AG-UI agents run on worker threads without a
     * security context, so each agent gets the user fixed at creation time (from the server-pinned
     * thread id) instead of guessing it per call.
     */
    public LongTermMemory forUser(String email) {
        return new LongTermMemory() {
            @Override
            public Mono<Void> record(List<Msg> messages) {
                return recordFor(email, messages);
            }

            @Override
            public Mono<String> retrieve(Msg msg) {
                return retrieveFor(email, msg);
            }
        };
    }

    @Override
    public Mono<Void> record(List<Msg> messages) {
        return recordFor(resolveEmailFromAuthentication(), messages);
    }

    @Override
    public Mono<String> retrieve(Msg msg) {
        return retrieveFor(resolveEmailFromAuthentication(), msg);
    }

    private Mono<Void> recordFor(String email, List<Msg> messages) {
        return Mono.<Void>fromRunnable(() -> {
                    if (!StringUtils.hasText(email) || messages == null || messages.isEmpty()) {
                        return;
                    }
                    persistLatestTurn(email, messages);
                    memoryService.ingestConversationSignals(email, messages);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(error -> {
                    log.warn("Failed to record long-term memory: {}", error.getMessage());
                    return Mono.<Void>empty();
                });
    }

    private Mono<String> retrieveFor(String email, Msg msg) {
        return Mono.fromSupplier(() -> {
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

    /**
     * Saves the newest user message and the final assistant reply to the chat history, so streamed
     * (AG-UI) conversations survive a page reload like REST ones do. {@code messages} is the agent's
     * whole short-term memory, so only the last exchange is written, and only once.
     */
    void persistLatestTurn(String email, List<Msg> messages) {
        int lastUser = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) != null && messages.get(i).getRole() == MsgRole.USER) {
                lastUser = i;
                break;
            }
        }
        if (lastUser < 0) {
            return;
        }
        String userText = messages.get(lastUser).getTextContent();
        String reply = null;
        for (int i = messages.size() - 1; i > lastUser; i--) {
            Msg m = messages.get(i);
            if (m != null && m.getRole() == MsgRole.ASSISTANT && StringUtils.hasText(m.getTextContent())) {
                reply = m.getTextContent();
                break;
            }
        }
        if (!StringUtils.hasText(userText) || !StringUtils.hasText(reply)) {
            return;
        }
        String userTextFinal = userText;
        String replyFinal = reply;
        userRepository.findByEmail(email).ifPresent(user -> {
            List<ChatMessage> latest = chatMessageRepository.findByUserIdOrderByTimestampDesc(user.getId(), PageRequest.of(0, 2));
            boolean alreadySaved = latest.size() == 2
                    && replyFinal.equals(latest.get(0).getContent())
                    && userTextFinal.equals(latest.get(1).getContent());
            if (alreadySaved) {
                return;
            }
            chatMessageRepository.save(ChatMessage.builder().user(user).role("user").content(userTextFinal).build());
            chatMessageRepository.save(ChatMessage.builder().user(user).role("ai").content(replyFinal).build());
        });
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
