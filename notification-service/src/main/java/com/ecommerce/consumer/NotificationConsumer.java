package com.ecommerce.consumer;

import com.ecommerce.events.*;
import com.ecommerce.service.NotificationService;
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
public class NotificationConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper        objectMapper;

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 10000),
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        dltTopicSuffix = ".dlq",
        exclude  = {IllegalArgumentException.class}
    )
    @KafkaListener(
        topics = "order.events",
        groupId = "notification-service-orders",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderEvent(ConsumerRecord<String, String> record) {
        DomainEvent event = deserialize(record.value());

        switch (event.getEventType()) {
            case "ORDER_CREATED"   ->
                notificationService.sendOrderCreatedNotification((OrderCreatedEvent) event);
            case "ORDER_CONFIRMED" ->
                notificationService.sendOrderConfirmedNotification((OrderConfirmedEvent) event);
            case "ORDER_CANCELLED" ->
                notificationService.sendOrderCancelledNotification((OrderCancelledEvent) event);
            default ->
                log.debug("Notification ignoring order event: {}", event.getEventType());
        }
    }

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 10000),
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        dltTopicSuffix = ".dlq",
        exclude  = {IllegalArgumentException.class}
    )
    @KafkaListener(
        topics = "payment.events",
        groupId = "notification-service-payments",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentEvent(ConsumerRecord<String, String> record) {
        DomainEvent event = deserialize(record.value());

        switch (event.getEventType()) {
            case "PAYMENT_COMPLETED" ->
                notificationService.sendPaymentSuccessNotification((PaymentCompletedEvent) event);
            case "PAYMENT_FAILED" ->
                notificationService.sendPaymentFailedNotification((PaymentFailedEvent) event);
            default ->
                log.debug("Notification ignoring payment event: {}", event.getEventType());
        }
    }

    @KafkaListener(
        topics = {"order.events.dlq", "payment.events.dlq"},
        groupId = "notification-service-dlq"
    )
    public void handleDlq(ConsumerRecord<String, String> record) {
        log.error("🚨 Notification DLQ — topic={}, key={}, offset={}, payload={}",
            record.topic(), record.key(), record.offset(), record.value());
        // In production: push alert to PagerDuty / Slack incident webhook
    }

    private DomainEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, DomainEvent.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unparseable event payload: " + e.getMessage(), e);
        }
    }
}
