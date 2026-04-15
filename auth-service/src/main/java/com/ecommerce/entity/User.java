package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_users_email", columnList = "email", unique = true)
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(length = 100) private String firstName;
    @Column(length = 100) private String lastName;

    @Column(nullable = false, length = 50) @Builder.Default
    private String role = "USER";

    @Column(nullable = false) @Builder.Default private boolean isActive = true;
    @Column(nullable = false) @Builder.Default private boolean isEmailVerified = false;

    @CreationTimestamp private Instant createdAt;
    @UpdateTimestamp   private Instant updatedAt;
}
