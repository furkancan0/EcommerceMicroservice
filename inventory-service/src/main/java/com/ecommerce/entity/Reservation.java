package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservations", indexes = {
    @Index(name = "idx_reservations_order_id",   columnList = "orderId"),
    @Index(name = "idx_reservations_product_id", columnList = "productId"),
    @Index(name = "idx_reservations_status",     columnList = "status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Reservation {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false) private UUID orderId;
    @Column(nullable = false) private UUID productId;
    @Column(nullable = false) private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) @Builder.Default
    private ReservationStatus status = ReservationStatus.RESERVED;

    @CreationTimestamp private Instant createdAt;
    private Instant releasedAt;

    public enum ReservationStatus {
        RESERVED, CONFIRMED, RELEASED, EXPIRED
    }

    public void release() {
        this.status = ReservationStatus.RELEASED;
        this.releasedAt = Instant.now();
    }

    public void confirm() {
        this.status = ReservationStatus.CONFIRMED;
    }
}
