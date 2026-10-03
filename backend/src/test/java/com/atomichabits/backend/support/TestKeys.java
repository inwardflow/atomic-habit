package com.atomichabits.backend.support;

import java.security.SecureRandom;
import java.util.Base64;

/** Test-only signing keys, generated per run so no key material is committed to the repository. */
public final class TestKeys {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TestKeys() {
    }

    /** A fresh Base64-encoded 256-bit key, valid for HS256. */
    public static String randomHs256Secret() {
        byte[] key = new byte[32];
        RANDOM.nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
