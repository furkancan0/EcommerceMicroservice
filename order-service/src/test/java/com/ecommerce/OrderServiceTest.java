package com.ecommerce;

import com.ecommerce.entity.Order;
import com.ecommerce.entity.Order.OrderStatus;
import com.ecommerce.events.*;
import com.ecommerce.lock.DistributedLockService;
import com.ecommerce.outbox.OutboxEvent;
import com.ecommerce.outbox.OutboxEventRepository;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ProcessedEventRepository;
import com.ecommerce.service.CreateOrderRequest;
import com.ecommerce.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService — Choreography Saga")
class OrderServiceTest {

    @Mock OrderRepository        orderRepo;
    @Mock OutboxEventRepository  outboxRepo;
    @Mock ProcessedEventRepository processedEventRepo;
    @Mock DistributedLockService lockService;

    OrderService orderService;

    @BeforeEach
    void setup() {
        ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
        orderService = new OrderService(
            orderRepo, outboxRepo, processedEventRepo, lockService, mapper);
    }

    // ─── Happy path ─────────────────────────────────────────────────────────

    @Nested @DisplayName("createOrder")
    class CreateOrder {

        @Test
        @DisplayName("creates order and enqueues OrderCreatedEvent in outbox")
        void createOrder_savesOrderAndOutboxEvent() {
            UUID userId = UUID.randomUUID();
            CreateOrderRequest req = CreateOrderRequest.builder()
                .totalAmount(new BigDecimal("99.99"))
                .currency("USD")
                .idempotencyKey("idem-123")
                .items(List.of(CreateOrderRequest.OrderItemRequest.builder()
                    .productId(UUID.randomUUID())
                    .productName("Widget")
                    .quantity(1)
                    .unitPrice(new BigDecimal("99.99"))
                    .build()))
                .build();

            Order savedOrder = Order.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .status(OrderStatus.PENDING)
                .totalAmount(req.getTotalAmount())
                .currency("USD")
                .build();

            // Distributed lock executes the supplier immediately in tests
            when(lockService.executeWithLock(anyString(), any(Supplier.class)))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
            when(orderRepo.findByIdempotencyKey("idem-123")).thenReturn(Optional.empty());
            when(orderRepo.save(any())).thenReturn(savedOrder);

            Order result = orderService.createOrder(req, userId);

            assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);

            // Verify outbox event was saved
            ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxRepo).save(outboxCaptor.capture());
            OutboxEvent outboxEvent = outboxCaptor.getValue();
            assertThat(outboxEvent.getEventType()).isEqualTo("ORDER_CREATED");
            assertThat(outboxEvent.getAggregateId()).isEqualTo(savedOrder.getId());
        }

        @Test
        @DisplayName("returns existing order on duplicate idempotency key")
        void createOrder_idempotent() {
            UUID userId = UUID.randomUUID();
            Order existing = Order.builder()
                .id(UUID.randomUUID())
                .status(OrderStatus.PENDING)
                .build();

            when(lockService.executeWithLock(anyString(), any(Supplier.class)))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
            when(orderRepo.findByIdempotencyKey("idem-dup")).thenReturn(Optional.of(existing));

            CreateOrderRequest req = CreateOrderRequest.builder()
                .idempotencyKey("idem-dup")
                .totalAmount(BigDecimal.ONE)
                .currency("USD")
                .items(List.of())
                .build();

            Order result = orderService.createOrder(req, userId);

            assertThat(result.getId()).isEqualTo(existing.getId());
            verify(orderRepo, never()).save(any()); // no new save
        }
    }

    // ─── Saga callbacks ─────────────────────────────────────────────────────

    @Nested @DisplayName("handlePaymentCompleted")
    class HandlePaymentCompleted {

        @Test
        @DisplayName("transitions order to CONFIRMED and publishes OrderConfirmedEvent")
        void paymentCompleted_confirmsOrder() {
            UUID orderId = UUID.randomUUID();
            Order order = Order.builder()
                .id(orderId)
                .userId(UUID.randomUUID())
                .status(OrderStatus.INVENTORY_RESERVED)
                .totalAmount(new BigDecimal("50.00"))
                .build();

            PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .orderId(orderId)
                .userId(order.getUserId())
                .paymentId(UUID.randomUUID())
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .provider("STRIPE")
                .build();
            event.setCorrelationId(orderId.toString());

            when(processedEventRepo.existsById(event.getEventId())).thenReturn(false);
            when(orderRepo.findByIdWithLock(orderId)).thenReturn(Optional.of(order));
            when(orderRepo.save(any())).thenReturn(order);

            orderService.handlePaymentCompleted(event);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxRepo).save(captor.capture());
            assertThat(captor.getValue().getEventType()).isEqualTo("ORDER_CONFIRMED");
        }
    }

    @Nested @DisplayName("handlePaymentFailed — compensation")
    class HandlePaymentFailed {

        @Test
        @DisplayName("cancels order and publishes OrderCancelledEvent for inventory rollback")
        void paymentFailed_compensates() {
            UUID orderId = UUID.randomUUID();
            Order order = Order.builder()
                .id(orderId)
                .userId(UUID.randomUUID())
                .status(OrderStatus.INVENTORY_RESERVED)
                .build();

            PaymentFailedEvent event = PaymentFailedEvent.builder()
                .orderId(orderId)
                .userId(order.getUserId())
                .reason("Insufficient funds")
                .errorCode("CARD_DECLINED")
                .provider("STRIPE")
                .build();

            when(processedEventRepo.existsById(event.getEventId())).thenReturn(false);
            when(orderRepo.findByIdWithLock(orderId)).thenReturn(Optional.of(order));
            when(orderRepo.save(any())).thenReturn(order);

            orderService.handlePaymentFailed(event);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);

            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxRepo).save(captor.capture());
            assertThat(captor.getValue().getEventType()).isEqualTo("ORDER_CANCELLED");
        }

        @Test
        @DisplayName("skips already-processed events (idempotent consumer)")
        void paymentFailed_idempotent() {
            PaymentFailedEvent event = PaymentFailedEvent.builder()
                .orderId(UUID.randomUUID())
                .reason("duplicate")
                .build();

            when(processedEventRepo.existsById(event.getEventId())).thenReturn(true);

            orderService.handlePaymentFailed(event);

            verify(orderRepo, never()).findByIdWithLock(any());
            verify(outboxRepo, never()).save(any());
        }
    }
}
