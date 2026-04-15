package com.ecommerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent consumer store.
 * INSERT ... ON CONFLICT DO NOTHING makes markProcessed safe under concurrent retries.
 */
@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO processed_events (event_id, processed_at, result_status)
        VALUES (:eventId, NOW(), :status)
        ON CONFLICT (event_id) DO NOTHING
        """, nativeQuery = true)
    void markProcessed(@Param("eventId") String eventId, @Param("status") String status);
}
