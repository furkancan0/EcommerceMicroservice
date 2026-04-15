package com.ecommerce.repository;

import com.ecommerce.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    /**
     * Used during saga callback processing to prevent concurrent state transitions
     * (e.g., two PaymentCompleted events racing to confirm the same order).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdWithLock(@Param("id") UUID id);

    Page<Order> findByUserId(UUID userId, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.userId = :userId AND o.status = :status")
    Page<Order> findByUserIdAndStatus(@Param("userId") UUID userId,
                                      @Param("status") Order.OrderStatus status,
                                      Pageable pageable);
}
