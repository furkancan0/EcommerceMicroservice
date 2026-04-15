package com.ecommerce.outbox;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events", indexes = {
    @Index(name = "idx_pay_outbox_status_created", columnList = "status, createdAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OutboxEvent {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false) private UUID aggregateId;
    @Column(nullable = false, length = 100) private String aggregateType;
    @Column(nullable = false, length = 100) private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) @Builder.Default
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(nullable = false) @Builder.Default
    private int retryCount = 0;

    @CreationTimestamp private Instant createdAt;
    private Instant publishedAt;
    private String errorMessage;

    public enum OutboxStatus { PENDING, PUBLISHED, FAILED }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    public void markFailed(String error) {
        this.status = OutboxStatus.FAILED;
        this.retryCount++;
        this.errorMessage = error;
    }
}
