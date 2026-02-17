package com.atomichabits.backend.controller;

import com.atomichabits.backend.service.CoachService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.atomichabits.backend.dto.ChatMessageResponse;
import com.atomichabits.backend.dto.MemoryHitResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.time.format.DateTimeFormatter;

import com.atomichabits.backend.dto.CoachMemoryResponse;
import com.atomichabits.backend.dto.WeeklyReviewResponse;
import com.atomichabits.backend.service.CoachTurnMemoryHitService;
import com.atomichabits.backend.service.MemoryService;

@RestController
@RequestMapping("/api/coach")
public class CoachController {

    private final CoachService coachService;
    private final MemoryService memoryService;
    private final CoachTurnMemoryHitService coachTurnMemoryHitService;

    public CoachController(CoachService coachService, MemoryService memoryService, CoachTurnMemoryHitService coachTurnMemoryHitService) {
        this.coachService = coachService;
        this.memoryService = memoryService;
        this.coachTurnMemoryHitService = coachTurnMemoryHitService;
    }

    @GetMapping("/memories")
    public ResponseEntity<List<CoachMemoryResponse>> getMemories(Authentication authentication) {
        List<CoachMemoryResponse> memories = memoryService.getRecentMemories(authentication.getName()).stream()
                .map(m -> CoachMemoryResponse.builder()
                        .id(m.getId())
                        .type(m.getType())
                        .content(m.getContent())
                        .referenceDate(m.getReferenceDate())
                        .createdAt(m.getCreatedAt())
                        .importanceScore(m.getImportanceScore())
                        .expiresAt(m.getExpiresAt())
                        .formattedDate(
                                m.getReferenceDate() != null
                                        ? m.getReferenceDate().format(DateTimeFormatter.ofPattern("MMM dd"))
                                        : (m.getCreatedAt() != null
                                        ? m.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM dd"))
                                        : "")
                        )
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(memories);
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(@RequestBody Map<String, String> request, Authentication authentication) {
        String message = request.get("message");
        String response = coachService.chat(authentication.getName(), message);
        return ResponseEntity.ok(Map.of("response", response));
    }

    @PostMapping("/weekly-review")
    public ResponseEntity<Map<String, String>> weeklyReview(Authentication authentication) {
        String response = coachService.weeklyReview(authentication.getName());
        return ResponseEntity.ok(Map.of("response", response));
    }

    @GetMapping("/weekly-reviews")
    public ResponseEntity<List<WeeklyReviewResponse>> getWeeklyReviews(
            Authentication authentication,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(coachService.getRecentWeeklyReviews(authentication.getName(), limit));
    }

    @GetMapping("/greeting")
    public ResponseEntity<Map<String, String>> getGreeting(Authentication authentication) {
        String response = coachService.generateGreeting(authentication.getName());
        return ResponseEntity.ok(Map.of("response", response));
    }

    @GetMapping("/history")
    public ResponseEntity<List<ChatMessageResponse>> getChatHistory(Authentication authentication) {
        List<ChatMessageResponse> history = coachService.getChatHistory(authentication.getName()).stream()
                .map(msg -> ChatMessageResponse.builder()
                        .role(msg.getRole())
                        .content(msg.getContent())
                        .timestamp(msg.getTimestamp())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(history);
    }

    @GetMapping("/memory-hits")
    public ResponseEntity<MemoryHitResponse> getMemoryHits(Authentication authentication) {
        var snapshot = coachTurnMemoryHitService.getLatestHits(authentication.getName());
        MemoryHitResponse response = MemoryHitResponse.builder()
                .hits(snapshot.hits())
                .updatedAt(snapshot.updatedAt())
                .build();
        return ResponseEntity.ok(response);
    }
}
