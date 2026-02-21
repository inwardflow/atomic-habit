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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }
    
    public List<SessionDto> getUserSessions(Long userId, String currentToken) {
        // Need to find the token itself to know which one is current, 
        // but we pass the raw token string for comparison.
        // Actually, we can just compare the token string if we have it.
        // The token stored in DB is the raw UUID string.
        
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
                        .isCurrent(token.getToken().equals(currentToken))
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

    public RefreshToken createRefreshToken(Long userId, String ipAddress, String deviceInfo, String deviceId) {
        RefreshToken refreshToken = new RefreshToken();

        refreshToken.setUser(userRepository.findById(userId)
                .orElseThrow(() -> new com.atomichabits.backend.exception.ResourceNotFoundException("User not found with id " + userId)));
        refreshToken.setExpiryDate(LocalDateTime.now().plusNanos(refreshTokenDurationMs * 1000000));
        refreshToken.setToken(UUID.randomUUID().toString());
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
        return refreshToken;
    }
    
    @Transactional
    public RefreshToken rotate(String token, String ipAddress, String deviceInfo) {
        Optional<RefreshToken> optionalToken = refreshTokenRepository.findByToken(token);
        
        if (optionalToken.isEmpty()) {
            throw new TokenRefreshException(token, "Refresh token is not in database!");
        }

        RefreshToken oldToken = optionalToken.get();
        String deviceId = oldToken.getDeviceId();
        
        // Invalidate old token
        refreshTokenRepository.delete(oldToken);
        
        // Create new token for same user, preserving deviceId
        return createRefreshToken(oldToken.getUser().getId(), ipAddress, deviceInfo, deviceId);
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(token);
            throw new TokenRefreshException(token.getToken(), "Refresh token was expired. Please make a new signin request");
        }
        return token;
    }

    @Transactional
    public void deleteByToken(String token) {
        refreshTokenRepository.deleteByToken(token);
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
