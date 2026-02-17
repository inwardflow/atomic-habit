package com.atomichabits.backend.controller;

import com.atomichabits.backend.dto.HabitRequest;
import com.atomichabits.backend.dto.HabitResponse;
import com.atomichabits.backend.dto.HabitStatsResponse;
import com.atomichabits.backend.service.HabitService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/habits")
public class HabitController {

    private final HabitService habitService;

    public HabitController(HabitService habitService) {
        this.habitService = habitService;
    }

    @PostMapping
    public ResponseEntity<HabitResponse> createHabit(@Valid @RequestBody HabitRequest request, Authentication authentication) {
        return ResponseEntity.ok(habitService.createHabit(authentication.getName(), request));
    }

    @PostMapping("/batch")
    public ResponseEntity<List<HabitResponse>> createHabits(@RequestBody List<HabitRequest> requests, Authentication authentication) {
        return ResponseEntity.ok(habitService.createHabits(authentication.getName(), requests));
    }

    @PutMapping("/{id}")
    public ResponseEntity<HabitResponse> updateHabit(@PathVariable Long id, @Valid @RequestBody HabitRequest request, Authentication authentication) {
        return ResponseEntity.ok(habitService.updateHabit(id, authentication.getName(), request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<HabitResponse> toggleHabitStatus(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(habitService.toggleHabitStatus(id, authentication.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteHabit(@PathVariable Long id, Authentication authentication) {
        habitService.deleteHabit(id, authentication.getName());
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<HabitResponse>> getHabits(Authentication authentication) {
        return ResponseEntity.ok(habitService.getUserHabits(authentication.getName()));
    }

    @GetMapping("/completions")
    public ResponseEntity<List<LocalDate>> getCompletions(Authentication authentication) {
        return ResponseEntity.ok(habitService.getAllCompletions(authentication.getName()));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<?> completeHabit(@PathVariable Long id, Authentication authentication) {
        habitService.completeHabit(id, authentication.getName());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/complete")
    public ResponseEntity<?> uncompleteHabit(@PathVariable Long id, Authentication authentication) {
        habitService.uncompleteHabit(id, authentication.getName());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<HabitStatsResponse> getHabitStats(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(habitService.getHabitStats(id, authentication.getName()));
    }
}
