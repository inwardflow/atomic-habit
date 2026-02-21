package com.atomichabits.backend.controller;

import com.atomichabits.backend.dto.AuthResponse;
import com.atomichabits.backend.dto.LoginRequest;
import com.atomichabits.backend.dto.RegisterRequest;
import com.atomichabits.backend.model.LoginHistory;
import com.atomichabits.backend.repository.LoginHistoryRepository;
import com.atomichabits.backend.service.AuthService;
import com.atomichabits.backend.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private RefreshTokenService refreshTokenService; // Still needed for sessions

    @Autowired
    private LoginHistoryRepository loginHistoryRepository; // Still needed for history query

    @Autowired
    private MessageSource messageSource;

    @Value("${app.auth.secure-cookie}")
    private boolean secureCookie;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        String ipAddress = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");

        AuthService.AuthResult result = authService.login(loginRequest, ipAddress, userAgent);

        ResponseCookie jwtCookie = createRefreshTokenCookie(result.getRefreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                .body(new AuthResponse(result.getAccessToken()));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(HttpServletRequest request) {
        String refreshToken = getCookieValue(request, "refresh_token");
        String ipAddress = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");

        if (refreshToken == null) {
             return ResponseEntity.badRequest().body("Refresh Token is empty!");
        }

        AuthService.AuthResult result = authService.refreshToken(refreshToken, ipAddress, userAgent);
        
        ResponseCookie jwtCookie = createRefreshTokenCookie(result.getRefreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                .body(new AuthResponse(result.getAccessToken()));
    }
    
    @PostMapping("/logout")
    public ResponseEntity<?> logoutUser(HttpServletRequest request) {
        String refreshToken = getRefreshTokenFromCookie(request);
        
        authService.logout(refreshToken);

        ResponseCookie jwtCookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                .body("Log out successful!");
    }

    
    @GetMapping("/login-history")
    public ResponseEntity<java.util.List<LoginHistory>> getLoginHistory(HttpServletRequest request) {
        String refreshToken = getCookieValue(request, "refresh_token");
        
        if (refreshToken == null) {
            return ResponseEntity.badRequest().build();
        }
        
        return refreshTokenService.findByToken(refreshToken)
                .map(token -> {
                    // Get latest 10 login records
                    org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
                    return ResponseEntity.ok(loginHistoryRepository.findByUserIdOrderByLoginTimeDesc(token.getUser().getId(), pageable).getContent());
                })
                .orElse(ResponseEntity.badRequest().build());
    }

    @GetMapping("/sessions")
    public ResponseEntity<java.util.List<com.atomichabits.backend.dto.SessionDto>> getSessions(HttpServletRequest request) {
        String refreshToken = getCookieValue(request, "refresh_token");
        
        if (refreshToken == null) {
            return ResponseEntity.badRequest().build();
        }
        
        return refreshTokenService.findByToken(refreshToken)
                .map(token -> ResponseEntity.ok(refreshTokenService.getUserSessions(token.getUser().getId(), refreshToken)))
                .orElse(ResponseEntity.badRequest().build());
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<?> revokeSession(HttpServletRequest request, @PathVariable Long id) {
        String refreshToken = getCookieValue(request, "refresh_token");
        
        if (refreshToken == null) {
            return ResponseEntity.badRequest().build();
        }
        
        return refreshTokenService.findByToken(refreshToken)
                .map(token -> {
                    refreshTokenService.deleteSession(id, token.getUser().getId());
                    return ResponseEntity.ok().build();
                })
                .orElse(ResponseEntity.badRequest().build());
    }
    
    @PostMapping("/logout-all")
    public ResponseEntity<?> logoutAll(HttpServletRequest request) {
        String refreshToken = getCookieValue(request, "refresh_token");
        
        if (refreshToken != null) {
            refreshTokenService.findByToken(refreshToken).ifPresent(token -> {
                refreshTokenService.deleteByUserId(token.getUser().getId());
            });
        }
        
        ResponseCookie jwtCookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
                
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                .body("Logged out from all devices!");
    }
    
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest registerRequest, HttpServletRequest request) {
        String ipAddress = request.getRemoteAddr();
        authService.register(registerRequest, ipAddress);
        String message = messageSource.getMessage("auth.register.success", null, "User registered successfully.", LocaleContextHolder.getLocale());
        return ResponseEntity.ok(Map.of("message", message != null ? message : "User registered successfully."));
    }

    private ResponseCookie createRefreshTokenCookie(String token) {
        return ResponseCookie.from("refresh_token", token)
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .maxAge(7 * 24 * 60 * 60)
                .sameSite("Lax")
                .build();
    }

    private String getCookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if (name.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private String getRefreshTokenFromCookie(HttpServletRequest request) {
        return getCookieValue(request, "refresh_token");
    }
}
