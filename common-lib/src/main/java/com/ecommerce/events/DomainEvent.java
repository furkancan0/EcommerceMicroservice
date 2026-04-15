package com.ecommerce.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
public abstract class DomainEvent {

    private String eventId = UUID.randomUUID().toString();
    private String correlationId;     // Saga correlation ID (= orderId)
    private String causationId;       // ID of the event that caused this one
    private String sourceService;
    private int version = 1;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant occurredAt = Instant.now();

    protected DomainEvent(String correlationId, String sourceService) {
        this.correlationId = correlationId;
        this.sourceService = sourceService;
    }

    public abstract String getEventType();

    public abstract String getAggregateType();
}
