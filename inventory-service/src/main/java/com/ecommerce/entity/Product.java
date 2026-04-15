package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products", indexes = {
    @Index(name = "idx_products_sku",      columnList = "sku",      unique = true),
    @Index(name = "idx_products_category", columnList = "category"),
    @Index(name = "idx_products_active",   columnList = "isActive")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Product {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String sku;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(length = 100)
    private String category;

    @Column(length = 500)
    private String imageUrl;

    @Column(nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @CreationTimestamp private Instant createdAt;
    @UpdateTimestamp   private Instant updatedAt;
}
