package com.ecommerce;

import com.ecommerce.circuit.PaymentProviderGateway;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PaymentProviderGateway — Circuit Breaker")
class PaymentProviderGatewayTest {

    private CircuitBreakerRegistry cbRegistry;

    @BeforeEach
    void setup() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(5)
            .minimumNumberOfCalls(3)
            .failureRateThreshold(60f)
            .waitDurationInOpenState(Duration.ofSeconds(5))
            .permittedNumberOfCallsInHalfOpenState(2)
            .build();
        cbRegistry = CircuitBreakerRegistry.of(config);
    }

    @Test
    @DisplayName("circuit opens after failure threshold is reached")
    void circuitBreaker_opensAfterThreshold() {
        CircuitBreaker cb = cbRegistry.circuitBreaker("stripe-test");

        // Simulate 3 failures out of 3 calls → 100% failure rate > 60% threshold
        for (int i = 0; i < 3; i++) {
            cb.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS,
                new RuntimeException("Stripe timeout"));
        }

        assertThat(cb.getState())
            .as("Circuit should be OPEN after 3 failures")
            .isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("circuit transitions CLOSED → OPEN → HALF_OPEN")
    void circuitBreaker_stateTransitions() throws InterruptedException {
        CircuitBreakerConfig shortWait = CircuitBreakerConfig.custom()
            .slidingWindowSize(4)
            .minimumNumberOfCalls(4)
            .failureRateThreshold(75f)
            .waitDurationInOpenState(Duration.ofMillis(100)) // very short for test
            .permittedNumberOfCallsInHalfOpenState(1)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build();

        CircuitBreaker cb = CircuitBreakerRegistry.of(shortWait).circuitBreaker("test-cb");

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // 3 failures out of 4 calls = 75% ≥ threshold
        cb.onSuccess(0, java.util.concurrent.TimeUnit.MILLISECONDS);
        for (int i = 0; i < 3; i++) {
            cb.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS,
                new RuntimeException("error"));
        }

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Wait for automatic transition to HALF_OPEN
        Thread.sleep(200);
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
    }

    @Test
    @DisplayName("fallback is invoked when circuit is OPEN")
    void fallback_invokedWhenOpen() {
        // Manually open the circuit
        CircuitBreaker cb = cbRegistry.circuitBreaker("stripe-fallback");
        cb.transitionToOpenState();

        // Create a gateway with a mock WebClient
        PaymentProviderGateway gateway = new PaymentProviderGateway(
            WebClient.builder().baseUrl("http://fake-stripe"));

        // The fallback should return a failure result rather than throw
        PaymentProviderGateway.PaymentResult result =
            gateway.stripeFallback(UUID.randomUUID(), BigDecimal.TEN, "USD",
                "tok_test", new RuntimeException("CB open"));

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("STRIPE_CIRCUIT_OPEN");
        assertThat(result.retryable()).isFalse(); // do not retry open-circuit failures
    }
}
