package com.atomichabits.backend.service;

import com.atomichabits.backend.exception.TokenRefreshException;
import com.atomichabits.backend.dto.SessionDto;
import com.atomichabits.backend.model.User;
import com.atomichabits.backend.model.RefreshToken;
import com.atomichabits.backend.repository.RefreshTokenRepository;
import com.atomichabits.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class RefreshTokenService {
    @Value("${spring.security.jwt.refresh-expiration-ms}")
    private Long refreshTokenDurationMs;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DeviceService deviceService;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * A newly issued refresh token: {@code value} goes to the client cookie and is never stored;
     * the persisted entity only holds its SHA-256 hash.
     */
    public record IssuedToken(String value, RefreshToken entity) {
    }

    /**
     * Refresh tokens are bearer credentials, so only their hash is stored: a leaked database dump
     * (or backup) cannot be replayed as live sessions.
     */
    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String newRawToken() {
        byte[] bytes = new byte[32]; // 256 bits
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Looks up a session by the raw token presented in the cookie. */
    public Optional<RefreshToken> findByToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return refreshTokenRepository.findByToken(hash(rawToken));
    }
    
    public List<SessionDto> getUserSessions(Long userId, String currentRawToken) {
        String currentHash = currentRawToken == null ? null : hash(currentRawToken);
        // Stored tokens are hashes, so compare against the hash of the presented token.
        User user = userRepository.findById(userId).orElseThrow();
        return refreshTokenRepository.findByUser(user).stream()
                .map(token -> SessionDto.builder()
                        .id(token.getId())
                        .ipAddress(token.getIpAddress())
                        .deviceInfo(token.getDeviceInfo())
                        .browser(token.getBrowser())
                        .operatingSystem(token.getOperatingSystem())
                        .deviceType(token.getDeviceType())
                        .location(token.getLocation())
                        .lastActive(token.getCreatedAt())
                        .isCurrent(token.getToken().equals(currentHash))
                        .build())
                .collect(Collectors.toList());
    }

    public void deleteSession(Long sessionId, Long userId) {
        refreshTokenRepository.findById(sessionId).ifPresent(token -> {
            if (token.getUser().getId().equals(userId)) {
                refreshTokenRepository.delete(token);
            }
        });
    }

    public IssuedToken createRefreshToken(Long userId, String ipAddress, String deviceInfo, String deviceId) {
        String rawToken = newRawToken();
        RefreshToken refreshToken = new RefreshToken();

        refreshToken.setUser(userRepository.findById(userId)
                .orElseThrow(() -> new com.atomichabits.backend.exception.ResourceNotFoundException("User not found with id " + userId)));
        refreshToken.setExpiryDate(LocalDateTime.now().plusNanos(refreshTokenDurationMs * 1000000));
        refreshToken.setToken(hash(rawToken));
        refreshToken.setIpAddress(ipAddress);
        refreshToken.setDeviceInfo(deviceInfo);
        refreshToken.setDeviceId(deviceId);
        
        // Parse device info
        com.atomichabits.backend.dto.DeviceMetadata metadata = deviceService.parseUserAgent(deviceInfo);
        refreshToken.setBrowser(metadata.getBrowser());
        refreshToken.setOperatingSystem(metadata.getOperatingSystem());
        refreshToken.setDeviceType(metadata.getDeviceType());
        refreshToken.setLocation(deviceService.getLocationFromIp(ipAddress));

        refreshToken = refreshTokenRepository.save(refreshToken);
        return new IssuedToken(rawToken, refreshToken);
    }
    
    @Transactional
    public IssuedToken rotate(String token, String ipAddress, String deviceInfo) {
        Optional<RefreshToken> optionalToken = findByToken(token);
        
        if (optionalToken.isEmpty()) {
            throw new TokenRefreshException(token, "Refresh token is not in database!");
        }

        RefreshToken oldToken = optionalToken.get();
        Long userId = oldToken.getUser().getId();
        String deviceId = oldToken.getDeviceId();

        // Invalidate old token. Only the request that actually deletes the row may rotate;
        // a concurrent request with the same token loses cleanly instead of failing with an
        // optimistic-locking 500.
        if (refreshTokenRepository.deleteByIdAndCount(oldToken.getId()) == 0) {
            throw new TokenRefreshException(token, "Refresh token was already used.");
        }

        // Create new token for same user, preserving deviceId
        return createRefreshToken(userId, ipAddress, deviceInfo, deviceId);
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(token);
            throw new TokenRefreshException(token.getToken(), "Refresh token was expired. Please make a new signin request");
        }
        return token;
    }

    @Transactional
    public void deleteByToken(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) {
            refreshTokenRepository.deleteByToken(hash(rawToken));
        }
    }
    
    @Transactional
    public int deleteByUserId(Long userId) {
        return refreshTokenRepository.deleteByUser(userRepository.findById(userId)
                .orElseThrow(() -> new com.atomichabits.backend.exception.ResourceNotFoundException("User not found with id " + userId)));
    }

    @Transactional
    public void deleteByUserIdAndDeviceInfo(Long userId, String deviceInfo) {
        User user = userRepository.findById(userId).orElseThrow();
        List<RefreshToken> existingTokens = refreshTokenRepository.findByUserAndDeviceInfo(user, deviceInfo);
        if (!existingTokens.isEmpty()) {
            refreshTokenRepository.deleteAll(existingTokens);
        }
    }

    @Transactional
    public void deleteByUserIdAndDeviceId(Long userId, String deviceId) {
        User user = userRepository.findById(userId).orElseThrow();
        refreshTokenRepository.findByUserAndDeviceId(user, deviceId)
                .ifPresent(refreshTokenRepository::delete);
    }
}
