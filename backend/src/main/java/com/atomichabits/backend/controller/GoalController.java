package com.atomichabits.backend.controller;

import com.atomichabits.backend.dto.GoalRequest;
import com.atomichabits.backend.dto.GoalResponse;
import com.atomichabits.backend.dto.HabitRequest;
import com.atomichabits.backend.service.GoalService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @PostMapping
    public ResponseEntity<GoalResponse> createGoal(Authentication authentication, @RequestBody GoalRequest request) {
        return ResponseEntity.ok(goalService.createGoal(authentication.getName(), request));
    }

    @GetMapping
    public ResponseEntity<List<GoalResponse>> getUserGoals(Authentication authentication) {
        return ResponseEntity.ok(goalService.getUserGoals(authentication.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GoalResponse> getGoal(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(goalService.getGoal(id, authentication.getName()));
    }

    @PostMapping("/{id}/habits")
    public ResponseEntity<GoalResponse> addHabitsToGoal(Authentication authentication, @PathVariable Long id, @RequestBody List<HabitRequest> habits) {
        return ResponseEntity.ok(goalService.addHabitsToGoal(id, habits, authentication.getName()));
    }
}
