package com.ecommerce.events;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InventoryReservedEvent extends DomainEvent {
    private UUID orderId;
    private UUID userId;
    private BigDecimal totalAmount;
    private String currency;
    private List<ReservedItem> reservedItems;

    @Override public String getEventType()     { return "INVENTORY_RESERVED"; }
    @Override public String getAggregateType() { return "INVENTORY"; }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ReservedItem {
        private UUID productId;
        private int quantity;
        private UUID reservationId;
    }
}
