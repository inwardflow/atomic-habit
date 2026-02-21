package com.atomichabits.backend.repository;

import com.atomichabits.backend.model.RefreshToken;
import com.atomichabits.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);

    @Modifying
    int deleteByUser(User user);
    
    @Modifying
    void deleteByToken(String token);

    java.util.List<RefreshToken> findByUser(User user);

    java.util.List<RefreshToken> findByUserAndDeviceInfo(User user, String deviceInfo);

    Optional<RefreshToken> findByUserAndDeviceId(User user, String deviceId);
}
