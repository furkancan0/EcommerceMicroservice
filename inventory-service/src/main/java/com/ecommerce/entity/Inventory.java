package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory", indexes = {
    @Index(name = "idx_inventory_product_id", columnList = "productId", unique = true)
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Inventory {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID productId;

    @Column(nullable = false) @Builder.Default
    private int totalQuantity = 0;

    @Column(nullable = false) @Builder.Default
    private int reservedQuantity = 0;

    @Column(nullable = false) @Builder.Default
    private int lowStockThreshold = 10;

    /** Optimistic lock — fallback if Redis lock is unavailable */
    @Version
    private Long version;

    @UpdateTimestamp
    private Instant updatedAt;

    public int getAvailableQuantity() {
        return Math.max(0, totalQuantity - reservedQuantity);
    }
}
