package com.ecommerce.events;

import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentFailedEvent extends DomainEvent {
    private UUID orderId;
    private UUID userId;
    private UUID paymentId;
    private BigDecimal amount;
    private String reason;
    private String errorCode;
    private String provider;

    @Override public String getEventType()     { return "PAYMENT_FAILED"; }
    @Override public String getAggregateType() { return "PAYMENT"; }
}
