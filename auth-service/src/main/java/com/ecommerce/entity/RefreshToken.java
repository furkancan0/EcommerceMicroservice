package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens", indexes = {
    @Index(name = "idx_refresh_tokens_hash", columnList = "tokenHash", unique = true),
    @Index(name = "idx_refresh_tokens_user",  columnList = "userId")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RefreshToken {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    /** SHA-256 hash — never store plain tokens */
    @Column(nullable = false, unique = true, length = 512)
    private String tokenHash;

    @CreationTimestamp
    private Instant issuedAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false) @Builder.Default
    private boolean isRevoked = false;

    @Column(length = 255)
    private String deviceInfo;
}
