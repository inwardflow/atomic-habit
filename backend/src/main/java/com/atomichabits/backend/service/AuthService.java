package com.atomichabits.backend.service;

import com.atomichabits.backend.dto.DeviceMetadata;
import com.atomichabits.backend.dto.LoginRequest;
import com.atomichabits.backend.dto.RegisterRequest;
import com.atomichabits.backend.exception.AccountLockedException;
import com.atomichabits.backend.exception.RateLimitExceededException;
import com.atomichabits.backend.exception.TokenRefreshException;
import com.atomichabits.backend.model.LoginHistory;
import com.atomichabits.backend.model.RefreshToken;
import com.atomichabits.backend.model.User;
import com.atomichabits.backend.repository.LoginHistoryRepository;
import com.atomichabits.backend.repository.UserRepository;
import com.atomichabits.backend.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;

@Service
public class AuthService {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private LoginHistoryRepository loginHistoryRepository;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private RateLimiterService rateLimiterService;

    public AuthResult login(LoginRequest loginRequest, String ipAddress, String userAgent) {
        if (rateLimiterService.isBlocked(ipAddress)) {
            String message = messageSource.getMessage("error.too.many.requests", null, "Too many requests. Please try again later.", LocaleContextHolder.getLocale());
            throw new RateLimitExceededException(message);
        }
        rateLimiterService.recordRequest(ipAddress);

        Optional<User> userOpt = userRepository.findByEmail(loginRequest.getEmail());
        if (userOpt.isPresent()) {
            long failedAttempts = loginHistoryRepository.countByUserAndStatusAndLoginTimeAfter(
                    userOpt.get(),
                    "FAILED",
                    LocalDateTime.now().minusMinutes(15)
            );

            if (failedAttempts >= 5) {
                String message = messageSource.getMessage("error.account.locked", null, "Account locked due to too many failed attempts. Please try again in 15 minutes.", LocaleContextHolder.getLocale());
                throw new AccountLockedException(message);
            }
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

            SecurityContextHolder.getContext().setAuthentication(authentication);

            String jwt = tokenProvider.generateToken(authentication);
            User user = userOpt.orElseThrow();

            String deviceId = loginRequest.getDeviceId();
            if (deviceId != null && !deviceId.trim().isEmpty()) {
                refreshTokenService.deleteByUserIdAndDeviceId(user.getId(), deviceId);
            } else {
                refreshTokenService.deleteByUserIdAndDeviceInfo(user.getId(), userAgent);
            }

            RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId(), ipAddress, userAgent, deviceId);

            recordLoginHistory(user, ipAddress, userAgent, "SUCCESS");

            return new AuthResult(jwt, refreshToken.getToken());

        } catch (Exception e) {
            userOpt.ifPresent(user -> recordLoginHistory(user, ipAddress, userAgent, "FAILED"));
            throw e;
        }
    }

    public AuthResult refreshToken(String refreshTokenStr, String ipAddress, String userAgent) {
        return refreshTokenService.findByToken(refreshTokenStr)
                .map(refreshTokenService::verifyExpiration)
                .map(token -> {
                    // Rotate the token: delete old, create new
                    RefreshToken newRefreshToken = refreshTokenService.rotate(token.getToken(), ipAddress, userAgent);

                    String accessToken = tokenProvider.generateTokenFromUsername(token.getUser().getEmail());

                    return new AuthResult(accessToken, newRefreshToken.getToken());
                })
                .orElseThrow(() -> new TokenRefreshException(refreshTokenStr, "Refresh token is not in database!"));
    }

    public void logout(String refreshTokenStr) {
        if (refreshTokenStr != null) {
            refreshTokenService.deleteByToken(refreshTokenStr);
        }
    }

    public void register(RegisterRequest registerRequest, String ipAddress) {
        if (rateLimiterService.isBlocked(ipAddress)) {
            String message = messageSource.getMessage("error.too.many.requests", null, "Too many requests. Please try again later.", LocaleContextHolder.getLocale());
            throw new RateLimitExceededException(message);
        }
        rateLimiterService.recordRequest(ipAddress);

        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            String message = messageSource.getMessage("auth.email.taken", null, "Email is already taken.", LocaleContextHolder.getLocale());
            throw new IllegalArgumentException(message);
        }

        User user = User.builder()
                .email(registerRequest.getEmail())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .identityStatement(registerRequest.getIdentityStatement())
                .roles(new HashSet<>(Collections.singletonList("ROLE_USER")))
                .build();

        userRepository.save(user);
    }

    private void recordLoginHistory(User user, String ipAddress, String userAgent, String status) {
        DeviceMetadata metadata = deviceService.parseUserAgent(userAgent);
        String location = deviceService.getLocationFromIp(ipAddress);

        LoginHistory history = LoginHistory.builder()
                .user(user)
                .ipAddress(ipAddress)
                .deviceInfo(userAgent)
                .browser(metadata.getBrowser())
                .operatingSystem(metadata.getOperatingSystem())
                .deviceType(metadata.getDeviceType())
                .location(location)
                .status(status)
                .build();
        loginHistoryRepository.save(history);
    }

    @lombok.Getter
    public static class AuthResult {
        private final String accessToken;
        private final String refreshToken;

        public AuthResult(String accessToken, String refreshToken) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }
    }
}
