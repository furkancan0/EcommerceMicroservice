package com.ecommerce.event;

import com.ecommerce.events.*;
import com.ecommerce.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSagaEventConsumer {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        dltTopicSuffix = ".dlq",
        exclude = {IllegalArgumentException.class}
    )
    @KafkaListener(
        topics = "payment.events",
        groupId = "order-service-payment",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumePaymentEvent(ConsumerRecord<String, String> record) {
        log.debug("Received payment event, key={}, partition={}, offset={}",
            record.key(), record.partition(), record.offset());

        try {
            DomainEvent event = objectMapper.readValue(record.value(), DomainEvent.class);

            switch (event.getEventType()) {
                case "PAYMENT_COMPLETED" ->
                    orderService.handlePaymentCompleted((PaymentCompletedEvent) event);
                case "PAYMENT_FAILED" ->
                    orderService.handlePaymentFailed((PaymentFailedEvent) event);
                default ->
                    log.debug("Order service ignoring payment event type: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("Error processing payment event from offset {}: {}", record.offset(), e.getMessage(), e);
            throw new RuntimeException("Failed to process payment event", e); // triggers retry
        }
    }

    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        dltTopicSuffix = ".dlq",
        exclude = {IllegalArgumentException.class}
    )
    @KafkaListener(
        topics = "inventory.events",
        groupId = "order-service-inventory",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeInventoryEvent(ConsumerRecord<String, String> record) {
        log.debug("Received inventory event, key={}", record.key());

        try {
            DomainEvent event = objectMapper.readValue(record.value(), DomainEvent.class);

            switch (event.getEventType()) {
                case "INVENTORY_RESERVED" ->
                    orderService.handleInventoryReserved((InventoryReservedEvent) event);
                case "INVENTORY_RESERVATION_FAILED" ->
                    orderService.handleInventoryReservationFailed((InventoryReservationFailedEvent) event);
                default ->
                    log.debug("Order service ignoring inventory event: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("Error processing inventory event: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process inventory event", e);
        }
    }


    @KafkaListener(
        topics = {"payment.events.dlq", "inventory.events.dlq"},
        groupId = "order-service-dlq"
    )
    public void handleDlq(ConsumerRecord<String, String> record) {
        log.error("🚨 DLQ message received — topic={}, key={}, value={}",
            record.topic(), record.key(), record.value());
        // In production: push to alerting (PagerDuty/Slack)
    }
}
