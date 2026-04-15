package com.ecommerce.repository;

import com.ecommerce.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying @Transactional
    @Query("UPDATE RefreshToken t SET t.isRevoked = true WHERE t.userId = :userId")
    void revokeAllForUser(@Param("userId") UUID userId);

    @Modifying @Transactional
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :cutoff AND t.isRevoked = true")
    void deleteExpiredAndRevoked(@Param("cutoff") Instant cutoff);
}
