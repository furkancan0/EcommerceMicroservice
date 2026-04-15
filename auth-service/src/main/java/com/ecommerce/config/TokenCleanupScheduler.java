package com.ecommerce.config;

import com.ecommerce.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
@Slf4j
@Component
@RequiredArgsConstructor
class TokenCleanupScheduler {

    private final RefreshTokenRepository refreshTokenRepo;

    @Scheduled(cron = "0 0 3 * * *")  // 3 AM daily
    @Transactional
    public void cleanupExpiredTokens() {
        Instant cutoff = Instant.now();
        refreshTokenRepo.deleteExpiredAndRevoked(cutoff);
        log.info("Cleaned up expired/revoked refresh tokens older than {}", cutoff);
    }
}
