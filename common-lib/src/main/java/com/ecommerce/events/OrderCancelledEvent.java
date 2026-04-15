package com.ecommerce.events;

import lombok.*;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderCancelledEvent extends DomainEvent {
    private UUID orderId;
    private UUID userId;
    private String reason;
    private String failedStep;  // INVENTORY | PAYMENT

    @Override public String getEventType()     { return "ORDER_CANCELLED"; }
    @Override public String getAggregateType() { return "ORDER"; }
}
