package com.ecommerce.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxEventRepository outboxRepo;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private static final Map<String, String> TOPIC_MAP = Map.of(
        "ORDER_CREATED",    "order.events",
        "ORDER_CONFIRMED",  "order.events",
        "ORDER_CANCELLED",  "order.events",
        "PAYMENT_COMPLETED","payment.events",
        "PAYMENT_FAILED",   "payment.events",
        "INVENTORY_RESERVED",         "inventory.events",
        "INVENTORY_RESERVATION_FAILED","inventory.events",
        "INVENTORY_RELEASED",          "inventory.events"
    );

    @Scheduled(fixedDelay = 500)  // runs every 500ms
    @Transactional
    public void publishPendingEvents() {
        // for multi-instance safety
        List<OutboxEvent> events = outboxRepo.findPendingEventsForUpdate(50);

        if (events.isEmpty()) return;


        for (OutboxEvent event : events) {
            String topic = TOPIC_MAP.getOrDefault(event.getEventType(), "order.events");

            try {
                CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(
                    topic,
                    event.getAggregateId().toString(),  // partition key = aggregateId
                    event.getPayload()
                );

                future.whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish event {} to topic {}: {}",
                            event.getId(), topic, ex.getMessage());
                        // Will be retried on next scheduler run
                    }
                });
                event.markPublished();

            } catch (Exception e) {
                log.error("Error publishing outbox event {}: {}", event.getId(), e.getMessage());
                event.markFailed(e.getMessage());

                if (event.getRetryCount() >= 5) {
                    log.error("Outbox event {} exceeded max retries, marking FAILED", event.getId());
                }
            }
        }
    }
}
