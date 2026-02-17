package com.atomichabits.backend.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtTokenProvider {

    @Value("${spring.security.jwt.secret}")
    private String jwtSecret;

    @Value("${spring.security.jwt.expiration}")
    private int jwtExpirationMs;

    private Key key() {
        // Use standard Base64 decoder if secret is hex/base64, or just bytes if plain text
        // For simplicity with the provided long hex-like string in yaml, let's treat it as bytes directly if long enough, 
        // or ensure it's used consistently.
        // The error 'WeakKeyException' might occur if key is too short for HS512.
        // The provided key in yaml is 64 chars hex string = 32 bytes = 256 bits. HS512 requires 512 bits (64 bytes).
        // Let's use a stronger key generation or ensure the secret is long enough.
        // For now, let's just use the bytes directly, but we need to make sure it's 512 bits for HS512.
        // Or switch to HS256 if the key is shorter.
        
        // Actually, let's decode the HEX string to bytes properly if it's hex.
        // But for safety and quick fix, let's just use the secret string bytes directly, 
        // and if it's too short for HS512, Keys.hmacShaKeyFor might complain or we should use HS256.
        
        // Let's switch to HS256 which requires 256 bits (32 bytes).
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    public String generateToken(Authentication authentication) {
        String username = authentication.getName();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(expiryDate)
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String getUserNameFromJwtToken(String token) {
        return Jwts.parserBuilder().setSigningKey(key()).build()
                .parseClaimsJws(token).getBody().getSubject();
    }

    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(authToken);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // Log exception
        }
        return false;
    }
}
