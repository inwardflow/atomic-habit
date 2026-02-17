package com.atomichabits.backend.controller;

import com.atomichabits.backend.dto.AdvancedUserStatsResponse;
import com.atomichabits.backend.dto.BadgeResponse;
import com.atomichabits.backend.dto.UserProfileResponse;
import com.atomichabits.backend.dto.UserStatsResponse;
import com.atomichabits.backend.service.GamificationService;
import com.atomichabits.backend.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        return ResponseEntity.ok(userService.updateIdentity(authentication.getName(), identityStatement));
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
        List<BadgeResponse> badges = gamificationService.getUserBadges(userId).stream()
                .map(b -> BadgeResponse.builder()
                        .id(b.getId())
                        .name(b.getName())
                        .description(b.getDescription())
                        .icon(b.getIcon())
                        .earnedAt(b.getEarnedAt())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(badges);
    }
}
