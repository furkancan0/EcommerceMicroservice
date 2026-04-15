package com.ecommerce.service;

import com.ecommerce.dto.*;
import com.ecommerce.entity.RefreshToken;
import com.ecommerce.entity.User;
import com.ecommerce.repository.RefreshTokenRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.security.JwtProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepo;
    private final RefreshTokenRepository refreshTokenRepo;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;

    private static final String BLACKLIST_PREFIX = "blacklist:";

    // Register
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepo.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already in use: " + request.getEmail());
        }

        User user = User.builder()
            .email(request.getEmail())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .role("USER")
            .build();

        user = userRepo.save(user);
        log.info("Registered new user: {}", user.getId());
        return issueTokens(user);
    }

    // Login
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepo.findByEmail(request.getEmail())
            .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!user.isActive()) {
            throw new IllegalStateException("Account is disabled");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        log.info("User {} logged in", user.getId());
        return issueTokens(user);
    }

    // Refresh
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        String tokenHash = hashToken(request.getRefreshToken());

        RefreshToken stored = refreshTokenRepo.findByTokenHash(tokenHash)
            .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Refresh token is expired or revoked");
        }

        User user = userRepo.findById(stored.getUserId())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Rotate refresh token (revoke old, issue new)
        stored.setRevoked(true);
        refreshTokenRepo.save(stored);

        return issueTokens(user);
    }

    // Logout
    @Transactional
    public void logout(String accessToken, String refreshToken) {
        try {
            Claims claims = jwtProvider.validateAndExtract(accessToken);
            String jti = claims.getId();

            // Calculate remaining TTL for the access token
            long remainingSeconds = claims.getExpiration().toInstant()
                .getEpochSecond() - Instant.now().getEpochSecond();

            if (remainingSeconds > 0) {
                // Add JTI to Redis blacklist — Gateway will reject it
                redisTemplate.opsForValue().set(
                    BLACKLIST_PREFIX + jti,
                    claims.getSubject(),
                    Duration.ofSeconds(remainingSeconds)
                );
                log.info("Blacklisted token JTI={} for {}s", jti, remainingSeconds);
            }
        } catch (Exception e) {
            log.warn("Could not blacklist access token during logout: {}", e.getMessage());
        }

        // Revoke refresh token if provided
        if (refreshToken != null) {
            String hash = hashToken(refreshToken);
            refreshTokenRepo.findByTokenHash(hash).ifPresent(t -> {
                t.setRevoked(true);
                refreshTokenRepo.save(t);
            });
        }
    }

    public boolean isTokenBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti));
    }

    // Private helpers
    private AuthResponse issueTokens(User user) {
        String accessToken  = jwtProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        String refreshToken = jwtProvider.generateRefreshToken(user.getId());

        // Store hashed refresh token
        RefreshToken storedToken = RefreshToken.builder()
            .userId(user.getId())
            .tokenHash(hashToken(refreshToken))
            .expiresAt(Instant.now().plusSeconds(7 * 24 * 60 * 60))
            .build();
        refreshTokenRepo.save(storedToken);

        return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .expiresIn(jwtProvider.getAccessTokenTtlSeconds())
            .userId(user.getId())
            .email(user.getEmail())
            .role(user.getRole())
            .build();
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Token hashing failed", e);
        }
    }
}
