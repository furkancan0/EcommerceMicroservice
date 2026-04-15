package com.ecommerce.repository;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "processed_events", indexes = {
    @Index(name = "idx_ord_processed_time", columnList = "processedAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProcessedEvent {
    @Id
    private String eventId;

    @Column(nullable = false)
    private Instant processedAt;

    @Column(length = 50)
    private String resultStatus;
}
