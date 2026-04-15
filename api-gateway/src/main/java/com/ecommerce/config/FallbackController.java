package com.ecommerce.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Fallback endpoints invoked by circuit breakers when downstream services are unavailable.
 * Returns a structured error response rather than letting the request time out.
 */
@Slf4j
@RestController
public class FallbackController {

    @RequestMapping("/fallback/orders")
    public Mono<ResponseEntity<Map<String, Object>>> ordersFallback() {
        log.warn("Circuit breaker triggered for order-service");
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                    "error", "ORDER_SERVICE_UNAVAILABLE",
                    "message", "Order service is temporarily unavailable. Please retry later.",
                    "timestamp", Instant.now().toString(),
                    "retryAfter", 30
                )));
    }

    @RequestMapping("/fallback/payments")
    public Mono<ResponseEntity<Map<String, Object>>> paymentsFallback() {
        log.warn("Circuit breaker triggered for payment-service");
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                    "error", "PAYMENT_SERVICE_UNAVAILABLE",
                    "message", "Payment service is temporarily unavailable. Your order has NOT been charged.",
                    "timestamp", Instant.now().toString(),
                    "retryAfter", 30
                )));
    }
}
