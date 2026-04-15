package com.ecommerce.service;

import com.ecommerce.cache.InventoryCacheService;
import com.ecommerce.entity.Inventory;
import com.ecommerce.entity.Reservation;
import com.ecommerce.entity.Reservation.ReservationStatus;
import com.ecommerce.events.*;
import com.ecommerce.repository.InventoryRepository;
import com.ecommerce.repository.OutboxEvent;
import com.ecommerce.repository.OutboxEventRepository;
import com.ecommerce.repository.ProcessedEventRepository;
import com.ecommerce.repository.ReservationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryReservationService {

    private final InventoryRepository      inventoryRepo;
    private final ReservationRepository    reservationRepo;
    private final OutboxEventRepository    outboxRepo;
    private final ProcessedEventRepository processedEventRepo;
    private final InventoryCacheService    cacheService;
    private final ObjectMapper             objectMapper;

    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        dltTopicSuffix = ".dlq",
        exclude  = {IllegalArgumentException.class}
    )
    @KafkaListener(
        topics = "order.events",
        groupId = "inventory-service-orders",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void onOrderEvent(String payload) {
        DomainEvent event;
        try {
            event = objectMapper.readValue(payload, DomainEvent.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unparseable order event payload", e);
        }

        switch (event.getEventType()) {
            case "ORDER_CREATED"   -> handleOrderCreated((OrderCreatedEvent) event);
            case "ORDER_CANCELLED" -> handleOrderCancelled((OrderCancelledEvent) event);
            default -> log.debug("Inventory ignoring event type: {}", event.getEventType());
        }
    }

    // Reserve (Saga Step 2 happy path)
    private void handleOrderCreated(OrderCreatedEvent event) {
        UUID orderId = event.getOrderId();

        if (processedEventRepo.existsById(event.getEventId())) {
            log.info("Duplicate OrderCreated {} — skipping", event.getEventId());
            return;
        }

        log.info("Reserving inventory for order {} ({} items)", orderId, event.getItems().size());
        List<InventoryReservedEvent.ReservedItem> reservedItems = new ArrayList<>();

        for (OrderCreatedEvent.OrderItem item : event.getItems()) {
            UUID productId = item.getProductId();

            // row-level lock prevents overselling
            Inventory inventory = inventoryRepo.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new IllegalArgumentException(
                    "Product not found in inventory: " + productId));

            int available = inventory.getAvailableQuantity();
            if (available < item.getQuantity()) {
                log.warn("Insufficient stock: product={} needed={} available={}",
                    productId, item.getQuantity(), available);
                publishReservationFailed(event, productId, item.getQuantity(), available);
                processedEventRepo.markProcessed(event.getEventId(), "INSUFFICIENT_STOCK");
                return;
            }

            inventory.setReservedQuantity(inventory.getReservedQuantity() + item.getQuantity());
            inventoryRepo.save(inventory);

            Reservation reservation = Reservation.builder()
                .orderId(orderId)
                .productId(productId)
                .quantity(item.getQuantity())
                .status(ReservationStatus.RESERVED)
                .build();
            reservation = reservationRepo.save(reservation);

            reservedItems.add(InventoryReservedEvent.ReservedItem.builder()
                .productId(productId)
                .quantity(item.getQuantity())
                .reservationId(reservation.getId())
                .build());

            cacheService.evictInventoryCache(productId);
        }

        InventoryReservedEvent reservedEvent = InventoryReservedEvent.builder()
            .orderId(orderId)
            .userId(event.getUserId())
            .totalAmount(event.getTotalAmount())
            .currency(event.getCurrency())
            .reservedItems(reservedItems)
            .build();
        reservedEvent.setCorrelationId(orderId.toString());
        reservedEvent.setSourceService("inventory-service");

        saveToOutbox(orderId, "INVENTORY", reservedEvent);
        processedEventRepo.markProcessed(event.getEventId(), "SUCCESS");
        log.info(" Inventory reserved for order {} ({} items)", orderId, reservedItems.size());
    }

    // Release (Saga Compensation)
    private void handleOrderCancelled(OrderCancelledEvent event) {
        UUID orderId = event.getOrderId();

        if (processedEventRepo.existsById(event.getEventId())) {
            log.info("Duplicate OrderCancelled {} — skipping", event.getEventId());
            return;
        }

        List<Reservation> reservations = reservationRepo
            .findByOrderIdAndStatus(orderId, ReservationStatus.RESERVED);

        if (reservations.isEmpty()) {
            log.info("No active reservations to release for order {}", orderId);
            processedEventRepo.markProcessed(event.getEventId(), "NO_RESERVATIONS");
            return;
        }

        for (Reservation reservation : reservations) {
            Inventory inventory = inventoryRepo
                .findByProductIdForUpdate(reservation.getProductId())
                .orElseThrow(() -> new IllegalStateException(
                    "Inventory row missing for product: " + reservation.getProductId()));

            inventory.setReservedQuantity(
                Math.max(0, inventory.getReservedQuantity() - reservation.getQuantity()));
            inventoryRepo.save(inventory);

            reservation.release();
            reservationRepo.save(reservation);
            cacheService.evictInventoryCache(reservation.getProductId());
        }

        InventoryReleasedEvent releasedEvent = InventoryReleasedEvent.builder()
            .orderId(orderId)
            .reason(event.getReason())
            .build();
        releasedEvent.setCorrelationId(orderId.toString());
        releasedEvent.setSourceService("inventory-service");

        saveToOutbox(orderId, "INVENTORY", releasedEvent);
        processedEventRepo.markProcessed(event.getEventId(), "SUCCESS");
        log.info(" Released {} reservations for cancelled order {}", reservations.size(), orderId);
    }

    // Helpers
    private void publishReservationFailed(OrderCreatedEvent event, UUID productId,
                                           int requested, int available) {
        InventoryReservationFailedEvent failedEvent = InventoryReservationFailedEvent.builder()
            .orderId(event.getOrderId())
            .userId(event.getUserId())
            .reason("Insufficient stock for product " + productId)
            .productId(productId)
            .requested(requested)
            .available(available)
            .build();
        failedEvent.setCorrelationId(event.getOrderId().toString());
        failedEvent.setSourceService("inventory-service");
        saveToOutbox(event.getOrderId(), "INVENTORY", failedEvent);
    }

    private void saveToOutbox(UUID aggregateId, String aggregateType, DomainEvent event) {
        try {
            outboxRepo.save(OutboxEvent.builder()
                .aggregateId(aggregateId)
                .aggregateType(aggregateType)
                .eventType(event.getEventType())
                .payload(objectMapper.writeValueAsString(event))
                .build());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event: " + event.getEventType(), e);
        }
    }

    @KafkaListener(topics = "order.events.dlq", groupId = "inventory-service-dlq")
    public void handleDlq(String payload) {
        log.error(" Inventory DLQ — manual intervention required: {}", payload);
    }
}
