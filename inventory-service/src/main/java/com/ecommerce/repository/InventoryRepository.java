package com.ecommerce.repository;

import com.ecommerce.entity.Inventory;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    Optional<Inventory> findByProductId(UUID productId);

    /**
     * SELECT ... FOR UPDATE — blocks concurrent writers on the same row.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.productId = :productId")
    Optional<Inventory> findByProductIdForUpdate(@Param("productId") UUID productId);

    @Query("SELECT (i.totalQuantity - i.reservedQuantity) FROM Inventory i WHERE i.productId = :productId")
    Optional<Integer> findAvailableQuantityByProductId(@Param("productId") UUID productId);
}
