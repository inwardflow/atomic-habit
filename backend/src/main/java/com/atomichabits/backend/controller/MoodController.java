package com.atomichabits.backend.controller;

import com.atomichabits.backend.service.MoodService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.bind.annotation.*;
import com.atomichabits.backend.model.MoodLog;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

@RestController
@RequestMapping("/api/moods")
public class MoodController {

    private final MoodService moodService;

    public MoodController(MoodService moodService) {
        this.moodService = moodService;
    }

    @PostMapping
    public ResponseEntity<MoodLog> logMood(@RequestBody Map<String, String> payload, Authentication authentication) {
        String moodType = payload.get("moodType");
        String note = payload.get("note");
        
        MoodLog log = moodService.logMood(authentication.getName(), moodType, note);
        return ResponseEntity.ok(log);
    }
    
    @GetMapping
    public ResponseEntity<Page<MoodLog>> getMoodHistory(
            Authentication authentication,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(moodService.getMoodHistory(authentication.getName(), pageable));
    }

    @GetMapping("/recent")
    public ResponseEntity<?> getRecentMoods(
            Authentication authentication,
            @RequestParam(required = false) String hours) {
        int parsedHours = 24;
        if (hours != null && !hours.isBlank()) {
            try {
                parsedHours = Integer.parseInt(hours);
            } catch (NumberFormatException ex) {
                Map<String, Object> body = new HashMap<>();
                body.put("message", "Invalid hours; expected an integer");
                return ResponseEntity.badRequest().body(body);
            }
        }
        parsedHours = Math.max(1, Math.min(parsedHours, 168));
        return ResponseEntity.ok(moodService.getRecentMoods(authentication.getName(), parsedHours));
    }

    @GetMapping("/since")
    public ResponseEntity<?> getMoodsSince(
            Authentication authentication,
            @RequestParam String since) {
        LocalDateTime parsedSince;
        try {
            parsedSince = LocalDateTime.parse(since, DateTimeFormatter.ISO_DATE_TIME);
        } catch (DateTimeParseException ex) {
            Map<String, Object> body = new HashMap<>();
            body.put("message", "Invalid since; expected ISO-8601 date-time like 2026-02-16T10:15:30");
            return ResponseEntity.badRequest().body(body);
        }
        return ResponseEntity.ok(moodService.getMoodsSince(authentication.getName(), parsedSince));
    }

    @GetMapping("/gratitude")
    public ResponseEntity<List<MoodLog>> getGratitudeLogs(Authentication authentication) {
        return ResponseEntity.ok(moodService.getGratitudeLogs(authentication.getName()));
    }

    @GetMapping("/gratitude/today")
    public ResponseEntity<MoodLog> getTodayGratitude(Authentication authentication) {
        MoodLog log = moodService.getTodayGratitude(authentication.getName());
        if (log == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(log);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MoodLog> updateMood(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload,
            Authentication authentication) {
        String note = payload.get("note");
        return ResponseEntity.ok(moodService.updateMood(id, note, authentication.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMood(@PathVariable Long id, Authentication authentication) {
        moodService.deleteMood(id, authentication.getName());
        return ResponseEntity.ok().build();
    }
}
