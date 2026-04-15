package com.ecommerce.service;

import com.ecommerce.entity.Order;
import com.ecommerce.entity.Order.OrderStatus;
import com.ecommerce.entity.OrderItem;
import com.ecommerce.events.*;
import com.ecommerce.exception.OrderNotFoundException;
import com.ecommerce.lock.DistributedLockService;
import com.ecommerce.outbox.OutboxEvent;
import com.ecommerce.outbox.OutboxEventRepository;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ProcessedEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepo;
    private final OutboxEventRepository outboxRepo;
    private final ProcessedEventRepository processedEventRepo;
    private final DistributedLockService lockService;
    private final ObjectMapper objectMapper;

    @Transactional
    public Order createOrder(CreateOrderRequest request, UUID userId) {
        String idempotencyKey = request.getIdempotencyKey();

        return lockService.executeWithLock(
            DistributedLockService.orderIdempotencyKey(idempotencyKey), () -> {

                // Idempotency check: return existing order if already created
                return orderRepo.findByIdempotencyKey(idempotencyKey)
                    .map(existing -> {
                        log.info("Idempotent order request detected, returning existing order: {}", existing.getId());
                        return existing;
                    })
                    .orElseGet(() -> doCreateOrder(request, userId, idempotencyKey));
            });
    }

    private Order doCreateOrder(CreateOrderRequest request, UUID userId, String idempotencyKey) {
        Order order = Order.builder()
            .userId(userId)
            .status(OrderStatus.PENDING)
            .totalAmount(request.getTotalAmount())
            .currency(request.getCurrency())
            .idempotencyKey(idempotencyKey)
            .build();

        request.getItems().forEach(item -> {
            OrderItem orderItem = OrderItem.builder()
                .productId(item.getProductId())
                .productName(item.getProductName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .totalPrice(item.getUnitPrice().multiply(java.math.BigDecimal.valueOf(item.getQuantity())))
                .build();
            order.addItem(orderItem);
        });

        Order savedOrder = orderRepo.save(order);

        OrderCreatedEvent event = OrderCreatedEvent.builder()
            .orderId(savedOrder.getId())
            .userId(userId)
            .totalAmount(savedOrder.getTotalAmount())
            .currency(savedOrder.getCurrency())
            .items(request.getItems().stream()
                .map(i -> OrderCreatedEvent.OrderItem.builder()
                    .productId(i.getProductId())
                    .productName(i.getProductName())
                    .quantity(i.getQuantity())
                    .unitPrice(i.getUnitPrice())
                    .build())
                .collect(Collectors.toList()))
            .build();
        event.setCorrelationId(savedOrder.getId().toString());
        event.setSourceService("order-service");

        // Persist to outbox (same transaction — atomicity guaranteed)
        saveToOutbox(savedOrder.getId(), "ORDER", event);

        return savedOrder;
    }

    @Transactional
    public void handleInventoryReserved(InventoryReservedEvent event) {
        if (isEventAlreadyProcessed(event.getEventId())) return;

        UUID orderId = event.getOrderId();
        Order order = orderRepo.findByIdWithLock(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() != OrderStatus.PENDING) {
            log.warn("Order {} is in status {}, ignoring InventoryReserved", orderId, order.getStatus());
            markEventProcessed(event.getEventId(), "SKIPPED");
            return;
        }

        order.setStatus(OrderStatus.INVENTORY_RESERVED);
        orderRepo.save(order);
        markEventProcessed(event.getEventId(), "SUCCESS");

    }

    @Transactional
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        if (isEventAlreadyProcessed(event.getEventId())) return;

        UUID orderId = event.getOrderId();
        Order order = orderRepo.findByIdWithLock(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() != OrderStatus.INVENTORY_RESERVED) {
            log.warn("Order {} unexpected status {} on PaymentCompleted", orderId, order.getStatus());
            markEventProcessed(event.getEventId(), "SKIPPED");
            return;
        }

        order.setStatus(OrderStatus.CONFIRMED);
        orderRepo.save(order);

        // Publish OrderConfirmed — triggers Notification Service
        OrderConfirmedEvent confirmedEvent = OrderConfirmedEvent.builder()
            .orderId(orderId)
            .userId(order.getUserId())
            .totalAmount(order.getTotalAmount())
            .build();
        confirmedEvent.setCorrelationId(orderId.toString());
        confirmedEvent.setSourceService("order-service");
        saveToOutbox(orderId, "ORDER", confirmedEvent);

        markEventProcessed(event.getEventId(), "SUCCESS");
    }

    @Transactional
    public void handlePaymentFailed(PaymentFailedEvent event) {
        if (isEventAlreadyProcessed(event.getEventId())) return;

        UUID orderId = event.getOrderId();
        Order order = orderRepo.findByIdWithLock(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            markEventProcessed(event.getEventId(), "ALREADY_CANCELLED");
            return;
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepo.save(order);

        // Compensation: publish OrderCancelled so Inventory Service releases stock
        OrderCancelledEvent cancelEvent = OrderCancelledEvent.builder()
            .orderId(orderId)
            .userId(order.getUserId())
            .reason("Payment failed: " + event.getReason())
            .failedStep("PAYMENT")
            .build();
        cancelEvent.setCorrelationId(orderId.toString());
        cancelEvent.setSourceService("order-service");
        saveToOutbox(orderId, "ORDER", cancelEvent);

        markEventProcessed(event.getEventId(), "SUCCESS");
    }

    @Transactional
    public void handleInventoryReservationFailed(InventoryReservationFailedEvent event) {
        if (isEventAlreadyProcessed(event.getEventId())) return;

        UUID orderId = event.getOrderId();
        Order order = orderRepo.findByIdWithLock(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

        order.setStatus(OrderStatus.CANCELLED);
        orderRepo.save(order);

        OrderCancelledEvent cancelEvent = OrderCancelledEvent.builder()
            .orderId(orderId)
            .userId(order.getUserId())
            .reason("Inventory unavailable: " + event.getReason())
            .failedStep("INVENTORY")
            .build();
        cancelEvent.setCorrelationId(orderId.toString());
        cancelEvent.setSourceService("order-service");
        saveToOutbox(orderId, "ORDER", cancelEvent);

        markEventProcessed(event.getEventId(), "SUCCESS");
    }

    private void saveToOutbox(UUID aggregateId, String aggregateType, DomainEvent event) {
        try {
            OutboxEvent outboxEvent = OutboxEvent.builder()
                .aggregateId(aggregateId)
                .aggregateType(aggregateType)
                .eventType(event.getEventType())
                .payload(objectMapper.writeValueAsString(event))
                .build();
            outboxRepo.save(outboxEvent);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event: " + event.getEventType(), e);
        }
    }

    private boolean isEventAlreadyProcessed(String eventId) {
        if (processedEventRepo.existsById(eventId)) {
            log.info("Idempotent skip — event {} already processed", eventId);
            return true;
        }
        return false;
    }

    private void markEventProcessed(String eventId, String status) {
        processedEventRepo.markProcessed(eventId, status);
    }
}
