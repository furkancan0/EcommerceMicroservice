package com.ecommerce.repository;

import com.ecommerce.entity.Reservation;
import com.ecommerce.entity.Reservation.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    List<Reservation> findByOrderId(UUID orderId);

    List<Reservation> findByOrderIdAndStatus(UUID orderId, ReservationStatus status);

    Optional<Reservation> findByOrderIdAndProductId(UUID orderId, UUID productId);

    boolean existsByOrderIdAndStatus(UUID orderId, ReservationStatus status);

    @Modifying
    @Transactional
    @Query("""
        UPDATE Reservation r
        SET r.status = 'EXPIRED', r.releasedAt = :now
        WHERE r.status = 'RESERVED'
        AND r.createdAt < :cutoff
        """)
    int expireStaleReservations(@Param("cutoff") Instant cutoff, @Param("now") Instant now);
}
