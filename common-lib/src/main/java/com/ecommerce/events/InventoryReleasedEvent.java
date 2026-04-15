package com.ecommerce.events;

import lombok.*;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InventoryReleasedEvent extends DomainEvent {
    private UUID orderId;
    private String reason;

    @Override public String getEventType()     { return "INVENTORY_RELEASED"; }
    @Override public String getAggregateType() { return "INVENTORY"; }
}
