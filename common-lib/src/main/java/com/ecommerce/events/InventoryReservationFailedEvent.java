package com.ecommerce.events;

import lombok.*;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InventoryReservationFailedEvent extends DomainEvent {
    private UUID orderId;
    private UUID userId;
    private String reason;
    private UUID productId;
    private int requested;
    private int available;

    @Override public String getEventType()     { return "INVENTORY_RESERVATION_FAILED"; }
    @Override public String getAggregateType() { return "INVENTORY"; }
}
