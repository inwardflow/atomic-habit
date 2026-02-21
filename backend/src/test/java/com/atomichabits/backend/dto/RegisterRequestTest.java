package com.atomichabits.backend.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RegisterRequestTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }
    
    @org.junit.jupiter.api.AfterAll
    static void tearDown() {
        if (validatorFactory != null) {
            validatorFactory.close();
        }
    }

    @Test
    void testWeakPassword() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setPassword("password"); // weak
        request.setIdentityStatement("I am a tester");

        var violations = validator.validate(request);
        assertFalse(violations.isEmpty(), "Weak password should be invalid");
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("{validation.password.pattern}")), "Should have password complexity message key");
    }

    @Test
    void testStrongPassword() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setPassword("StrongPass1!"); // strong
        request.setIdentityStatement("I am a tester");

        var violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Strong password should be valid");
    }
}
