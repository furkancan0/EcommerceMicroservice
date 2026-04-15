package com.ecommerce.events;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderCreatedEvent extends DomainEvent {
    private UUID orderId;
    private UUID userId;
    private BigDecimal totalAmount;
    private String currency;
    private List<OrderItem> items;

    @Override public String getEventType()     { return "ORDER_CREATED"; }
    @Override public String getAggregateType() { return "ORDER"; }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class OrderItem {
        private UUID productId;
        private String productName;
        private int quantity;
        private BigDecimal unitPrice;
    }
}
