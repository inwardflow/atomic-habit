package com.atomichabits.backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final String OTHER_SECRET = "5A7134743777217A25432A462D4A614E645267556B58703273357638792F423F";

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = newProvider(SECRET, 60_000);
    }

    @Test
    void generatedTokenRoundTrips() {
        String token = provider.generateTokenFromUsername("alice@example.com");

        assertThat(provider.validateJwtToken(token)).isTrue();
        assertThat(provider.getUserNameFromJwtToken(token)).isEqualTo("alice@example.com");
    }

    @Test
    void tokenSignedWithDifferentKeyIsRejected() {
        String forged = newProvider(OTHER_SECRET, 60_000).generateTokenFromUsername("alice@example.com");

        assertThat(provider.validateJwtToken(forged)).isFalse();
    }

    @Test
    void expiredTokenIsRejected() {
        String expired = newProvider(SECRET, -1_000).generateTokenFromUsername("alice@example.com");

        assertThat(provider.validateJwtToken(expired)).isFalse();
    }

    @Test
    void malformedOrEmptyTokenIsRejected() {
        assertThat(provider.validateJwtToken("not-a-jwt")).isFalse();
        assertThat(provider.validateJwtToken("")).isFalse();
    }

    private static JwtTokenProvider newProvider(String secret, int expirationMs) {
        JwtTokenProvider p = new JwtTokenProvider();
        ReflectionTestUtils.setField(p, "jwtSecret", secret);
        ReflectionTestUtils.setField(p, "jwtExpirationMs", expirationMs);
        return p;
    }
}
