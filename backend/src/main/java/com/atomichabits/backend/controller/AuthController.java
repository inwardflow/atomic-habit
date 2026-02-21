package com.atomichabits.backend.controller;

import com.atomichabits.backend.dto.AuthResponse;
import com.atomichabits.backend.dto.LoginRequest;
import com.atomichabits.backend.dto.RegisterRequest;
import com.atomichabits.backend.model.User;
import com.atomichabits.backend.repository.UserRepository;
import com.atomichabits.backend.security.JwtTokenProvider;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final MessageSource messageSource;

    public AuthController(AuthenticationManager authenticationManager, UserRepository userRepository,
                          PasswordEncoder passwordEncoder, JwtTokenProvider tokenProvider, MessageSource messageSource) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.messageSource = messageSource;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getEmail(),
                        loginRequest.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = tokenProvider.generateToken(authentication);
        return ResponseEntity.ok(new AuthResponse(jwt));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest registerRequest) {
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            String message = messageSource.getMessage("auth.email.taken", null, "Email is already taken.", LocaleContextHolder.getLocale());
            return ResponseEntity.badRequest().body(Map.of("error", message));
        }

        User user = User.builder()
                .email(registerRequest.getEmail())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .identityStatement(registerRequest.getIdentityStatement())
                .build();

        userRepository.save(user);

        String message = messageSource.getMessage("auth.register.success", null, "User registered successfully.", LocaleContextHolder.getLocale());
        return ResponseEntity.ok(Map.of("message", message));
    }
}
