package com.ecommerce.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Distributed Lock Service backed by Redis (Redisson).
 * Prevent duplicate order creation with the same idempotency key
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockService {

    private final RedissonClient redissonClient;

    private static final String LOCK_PREFIX    = "lock:";
    private static final long   WAIT_SECONDS   = 5;
    private static final long   LEASE_SECONDS  = 10;


    public <T> T executeWithLock(String lockKey, Supplier<T> task) {
        String fullKey = LOCK_PREFIX + lockKey;
        RLock lock = redissonClient.getLock(fullKey);

        boolean acquired = false;
        try {
            acquired = lock.tryLock(WAIT_SECONDS, LEASE_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("Could not acquire distributed lock for key: {}", fullKey);
                throw new IllegalStateException("Resource is locked by another request. Try again shortly.");
            }
            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for lock: " + fullKey, e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public void executeWithLock(String lockKey, Runnable task) {
        executeWithLock(lockKey, () -> {
            task.run();
            return null;
        });
    }

    public static String orderIdempotencyKey(String idempotencyKey) {
        return "order:idempotency:" + idempotencyKey;
    }

    public static String inventoryLockKey(UUID productId) {
        return "inventory:product:" + productId;
    }

    public static String paymentLockKey(UUID orderId) {
        return "payment:order:" + orderId;
    }
}
