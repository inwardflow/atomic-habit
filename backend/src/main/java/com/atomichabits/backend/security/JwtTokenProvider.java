package com.atomichabits.backend.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

/**
 * Generates and validates JSON Web Tokens (JWT) for user authentication.
 *
 * <p>The signing key is read from {@code spring.security.jwt.secret} and
 * must be a Base64-encoded string of at least 256 bits (32 bytes) for HS256.</p>
 */
@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${spring.security.jwt.secret}")
    private String jwtSecret;

    @Value("${spring.security.jwt.expiration}")
    private int jwtExpirationMs;

    /**
     * Fails fast at startup if the configured secret is too weak for HS256,
     * instead of surfacing a {@code WeakKeyException} on the first login.
     */
    @PostConstruct
    void validateSecret() {
        key();
    }

    private SecretKey key() {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(jwtSecret);
        } catch (IllegalArgumentException e) {
            // Fallback: treat the secret as a plain UTF-8 string.
            keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(Authentication authentication) {
        String username = authentication.getName();
        return generateTokenFromUsername(username);
    }

    public String generateTokenFromUsername(String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key(), Jwts.SIG.HS256)
                .compact();
    }

    public String getUserNameFromJwtToken(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parser().verifyWith(key()).build().parseSignedClaims(authToken);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("JWT token is expired: {}", e.getMessage());
        } catch (JwtException e) {
            // Covers malformed, unsupported and bad-signature tokens.
            log.warn("Invalid JWT token: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }
}
