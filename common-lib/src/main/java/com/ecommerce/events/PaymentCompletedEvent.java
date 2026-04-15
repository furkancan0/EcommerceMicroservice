package com.ecommerce.events;

import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentCompletedEvent extends DomainEvent {
    private UUID orderId;
    private UUID userId;
    private UUID paymentId;
    private BigDecimal amount;
    private String currency;
    private String provider;
    private String providerTransactionId;

    @Override public String getEventType()     { return "PAYMENT_COMPLETED"; }
    @Override public String getAggregateType() { return "PAYMENT"; }
}
