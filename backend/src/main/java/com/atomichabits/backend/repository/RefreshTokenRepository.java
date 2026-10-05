package com.atomichabits.backend.repository;

import com.atomichabits.backend.model.RefreshToken;
import com.atomichabits.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);

    @Modifying
    int deleteByUser(User user);
    
    @Modifying
    void deleteByToken(String token);

    /**
     * Bulk delete that reports whether this caller actually removed the row; used to make
     * rotation race-safe when two requests present the same refresh token concurrently.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshToken t where t.id = :id")
    int deleteByIdAndCount(@Param("id") Long id);

    java.util.List<RefreshToken> findByUser(User user);

    java.util.List<RefreshToken> findByUserAndDeviceInfo(User user, String deviceInfo);

    Optional<RefreshToken> findByUserAndDeviceId(User user, String deviceId);
}
