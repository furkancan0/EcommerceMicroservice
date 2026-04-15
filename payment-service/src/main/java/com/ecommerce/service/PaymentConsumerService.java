package com.ecommerce.service;

import com.ecommerce.circuit.PaymentProviderGateway;
import com.ecommerce.circuit.PaymentProviderGateway.PaymentResult;
import com.ecommerce.entity.Payment;
import com.ecommerce.entity.Payment.PaymentStatus;
import com.ecommerce.events.*;
import com.ecommerce.outbox.OutboxEvent;
import com.ecommerce.outbox.OutboxEventRepository;
import com.ecommerce.repository.PaymentRepository;
import com.ecommerce.repository.ProcessedEventRepository;
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

import java.util.Random;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentConsumerService {

    private final PaymentRepository paymentRepo;
    private final OutboxEventRepository outboxRepo;
    private final ProcessedEventRepository processedEventRepo;
    private final PaymentProviderGateway providerGateway;
    private final ObjectMapper objectMapper;

    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 15000),
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        dltTopicSuffix = ".dlq",
        exclude = {IllegalArgumentException.class}
    )
    @KafkaListener(
        topics = "inventory.events",
        groupId = "payment-service-inventory",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void onInventoryEvent(String payload) {
        DomainEvent event;
        try {
            event = objectMapper.readValue(payload, DomainEvent.class);
        } catch (Exception e) {
            log.error("Cannot deserialize inventory event: {}", e.getMessage());
            throw new IllegalArgumentException("Unparseable event payload", e);
        }

        if (!"INVENTORY_RESERVED".equals(event.getEventType())) {
            return;
        }

        InventoryReservedEvent inventoryEvent = (InventoryReservedEvent) event;
        UUID orderId = inventoryEvent.getOrderId();

        // Idempotency check
        if (processedEventRepo.existsById(event.getEventId())) {
            log.info("Payment event {} already processed — skipping", event.getEventId());
            return;
        }

        processPayment(inventoryEvent);
    }

    private void processPayment(InventoryReservedEvent event) {
        UUID orderId = event.getOrderId();

        if (paymentRepo.existsByOrderId(orderId)) {
            log.warn("Payment for order {} already exists — skipping duplicate", orderId);
            processedEventRepo.markProcessed(event.getEventId(), "DUPLICATE");
            return;
        }

        Payment payment = Payment.builder()
            .orderId(orderId)
            .userId(event.getUserId())
            .amount(event.getTotalAmount())
            .currency(event.getCurrency())
            .provider("STRIPE")  // TODO: from user preference
            .status(PaymentStatus.PROCESSING)
            .build();
        payment = paymentRepo.save(payment);

        // Call external provider via Circuit Breaker
        PaymentResult result = providerGateway.chargeViaStripe(
            orderId, event.getTotalAmount(), event.getCurrency(), "tok_test_stripe"
        );

        if (result.success()) {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setProviderTransactionId(result.transactionId());
            paymentRepo.save(payment);

            PaymentCompletedEvent completedEvent = PaymentCompletedEvent.builder()
                .orderId(orderId)
                .userId(event.getUserId())
                .paymentId(payment.getId())
                .amount(event.getTotalAmount())
                .currency(event.getCurrency())
                .provider(result.provider())
                .providerTransactionId(result.transactionId())
                .build();
            completedEvent.setCorrelationId(orderId.toString());
            completedEvent.setSourceService("payment-service");

            saveToOutbox(orderId, "PAYMENT", completedEvent);
            log.info("✅ Payment {} completed for order {}", payment.getId(), orderId);

        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(result.errorCode() + ": " + result.errorMessage());
            paymentRepo.save(payment);

            PaymentFailedEvent failedEvent = PaymentFailedEvent.builder()
                .orderId(orderId)
                .userId(event.getUserId())
                .paymentId(payment.getId())
                .amount(event.getTotalAmount())
                .reason(result.errorMessage())
                .errorCode(result.errorCode())
                .provider(result.provider() != null ? result.provider() : "UNKNOWN")
                .build();
            failedEvent.setCorrelationId(orderId.toString());
            failedEvent.setSourceService("payment-service");

            saveToOutbox(orderId, "PAYMENT", failedEvent);

            // If transient failure and CB is retryable, re-throw to trigger Kafka retry
            if (result.retryable()) {
                throw new RuntimeException("Transient payment failure for order " + orderId
                    + " — will retry via Kafka");
            }
        }

        processedEventRepo.markProcessed(event.getEventId(), "SUCCESS");
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
            throw new RuntimeException("Failed to serialize outbox event", e);
        }
    }

    @KafkaListener(topics = "inventory.events.dlq", groupId = "payment-service-dlq")
    public void handleDlq(String payload) {
        log.error("Payment DLQ message — manual intervention needed: {}", payload);
    }

    private int randomInt(int min, int max){
        int chance = (int)(Math.random() * (max - min + 1)) + min;
        return chance;
    }
}
