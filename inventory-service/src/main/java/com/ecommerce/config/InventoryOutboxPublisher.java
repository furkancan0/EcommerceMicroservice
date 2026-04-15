package com.ecommerce.config;

// ─────────────────────────────────────────────────────────────────────────────
//  Outbox Publisher — SELECT FOR UPDATE SKIP LOCKED relay to Kafka
// ─────────────────────────────────────────────────────────────────────────────

import com.ecommerce.repository.OutboxEvent;
import com.ecommerce.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
class InventoryOutboxPublisher {

    private final OutboxEventRepository outboxRepo;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private static final Map<String, String> TOPIC_MAP = Map.of(
            "INVENTORY_RESERVED",          "inventory.events",
            "INVENTORY_RESERVATION_FAILED","inventory.events",
            "INVENTORY_RELEASED",          "inventory.events"
    );

    @Scheduled(fixedDelay = 500)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxRepo.findPendingEventsForUpdate(50);
        if (events.isEmpty()) return;

        for (OutboxEvent event : events) {
            String topic = TOPIC_MAP.getOrDefault(event.getEventType(), "inventory.events");
            try {
                kafkaTemplate.send(topic, event.getAggregateId().toString(), event.getPayload());
                event.markPublished();
            } catch (Exception e) {
                event.markFailed(e.getMessage());
                log.error("Failed to publish inventory outbox event {}: {}", event.getId(), e.getMessage());
            }
        }
    }
}
