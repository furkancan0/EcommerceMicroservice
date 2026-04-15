package com.ecommerce.events;

import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderConfirmedEvent extends DomainEvent {
    private UUID orderId;
    private UUID userId;
    private BigDecimal totalAmount;

    @Override public String getEventType()     { return "ORDER_CONFIRMED"; }
    @Override public String getAggregateType() { return "ORDER"; }
}
