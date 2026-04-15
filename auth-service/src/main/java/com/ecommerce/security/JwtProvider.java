package com.ecommerce.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtProvider {

    private final PrivateKey privateKey;
    private final PublicKey  publicKey;

    private static final long ACCESS_TOKEN_TTL_SECONDS  = 45 * 60;         // 45 min
    private static final long REFRESH_TOKEN_TTL_SECONDS = 7 * 24 * 60 * 60; // 7 days

    public JwtProvider(
            @Value("${jwt.private-key-path}") String privateKeyPath,
            @Value("${jwt.public-key-path}")  String publicKeyPath) throws Exception {
        this.privateKey = loadPrivateKey("keys/private_key.pem");
        this.publicKey  = loadPublicKey("keys/public_key.pem");
    }

    public String generateAccessToken(UUID userId, String email, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .claim("role", role)
            .claim("type", "ACCESS")
            .id(UUID.randomUUID().toString())           // jti for blacklisting
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(ACCESS_TOKEN_TTL_SECONDS)))
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact();
    }

    public String generateRefreshToken(UUID userId) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(userId.toString())
            .claim("type", "REFRESH")
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(REFRESH_TOKEN_TTL_SECONDS)))
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact();
    }

    public Claims validateAndExtract(String token) {
        return Jwts.parser()
            .verifyWith(publicKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public boolean isAccessToken(Claims claims) {
        return "ACCESS".equals(claims.get("type", String.class));
    }

    public long getAccessTokenTtlSeconds() {
        return ACCESS_TOKEN_TTL_SECONDS;
    }

    // ─── Key Loading ───────────────────────────────────────────────────

    private PrivateKey loadPrivateKey(String path) throws Exception {
        String key = Files.readString(Path.of(path))
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(key);
        return KeyFactory.getInstance("RSA")
            .generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    private PublicKey loadPublicKey(String path) throws Exception {
        String key = Files.readString(Path.of(path))
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(key);
        return KeyFactory.getInstance("RSA")
            .generatePublic(new X509EncodedKeySpec(decoded));
    }
}
