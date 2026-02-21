package com.atomichabits.backend.controller;

import com.atomichabits.backend.dto.AdvancedUserStatsResponse;
import com.atomichabits.backend.dto.BadgeResponse;
import com.atomichabits.backend.dto.ChangePasswordRequest;
import com.atomichabits.backend.dto.UserProfileResponse;
import com.atomichabits.backend.dto.UserStatsResponse;
import com.atomichabits.backend.service.GamificationService;
import com.atomichabits.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final GamificationService gamificationService;

    public UserController(UserService userService, GamificationService gamificationService) {
        this.userService = userService;
        this.gamificationService = gamificationService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser(Authentication authentication) {
        return ResponseEntity.ok(userService.getUserProfile(authentication.getName()));
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateCurrentUser(@RequestBody Map<String, String> request, Authentication authentication) {
        String identityStatement = request.get("identityStatement");
        String email = request.get("email");
        return ResponseEntity.ok(userService.updateProfile(authentication.getName(), identityStatement, email));
    }

    @PostMapping("/me/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {
        userService.changePassword(authentication.getName(), request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }

    @PostMapping("/me/delete-account")
    public ResponseEntity<Map<String, String>> deleteAccount(
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        String password = request.get("password");
        userService.deleteAccount(authentication.getName(), password);
        return ResponseEntity.ok(Map.of("message", "Account deleted successfully"));
    }

    @GetMapping("/stats")
    public ResponseEntity<UserStatsResponse> getUserStats(Authentication authentication) {
        return ResponseEntity.ok(userService.getUserStats(authentication.getName()));
    }

    @GetMapping("/stats/advanced")
    public ResponseEntity<AdvancedUserStatsResponse> getAdvancedStats(Authentication authentication) {
        return ResponseEntity.ok(userService.getAdvancedStats(authentication.getName()));
    }

    @GetMapping("/badges")
    public ResponseEntity<List<BadgeResponse>> getUserBadges(Authentication authentication) {
        Long userId = userService.getUserProfile(authentication.getName()).getId();
        List<BadgeResponse> badges = gamificationService.getLocalizedUserBadges(userId);
        return ResponseEntity.ok(badges);
    }
}
