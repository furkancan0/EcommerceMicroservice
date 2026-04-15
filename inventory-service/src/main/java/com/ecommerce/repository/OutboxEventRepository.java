package com.ecommerce.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
        SELECT e FROM OutboxEvent e
        WHERE e.status = 'PENDING' AND e.retryCount < 5
        ORDER BY e.createdAt ASC
        LIMIT :batchSize
        """)
    List<OutboxEvent> findPendingEventsForUpdate(@Param("batchSize") int batchSize);

    @Query("SELECT COUNT(e) FROM OutboxEvent e WHERE e.status = 'PENDING'")
    long countPending();
}
