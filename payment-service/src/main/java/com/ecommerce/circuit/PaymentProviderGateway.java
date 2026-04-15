package com.ecommerce.circuit;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentProviderGateway {

    private final WebClient.Builder webClientBuilder;

    @Value("${paypal.base-url:https://api-m.sandbox.paypal.com}")
    private String paypalBaseUrl;

    @Value("${stripe.base-url:https://api.stripe.com}")
    private String stripeBaseUrl;


    @CircuitBreaker(name = "paypal", fallbackMethod = "paypalFallback")
    @Retry(name = "paypal")
    public PaymentResult chargeViaPaypal(UUID orderId, BigDecimal amount, String currency,
                                          String paypalToken) {
        try {
            Map<String, Object> requestBody = Map.of(
                "intent", "CAPTURE",
                "purchase_units", new Object[]{Map.of(
                    "reference_id", orderId.toString(),
                    "amount", Map.of("currency_code", currency, "value", amount.toPlainString())
                )}
            );

            Map<?, ?> response = webClientBuilder.build()
                .post()
                .uri(paypalBaseUrl + "/v2/checkout/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(10))
                .block();

            String transactionId = (String) response.get("id");
            return PaymentResult.success(transactionId, "PAYPAL");

        } catch (WebClientResponseException.UnprocessableEntity e) {
            // 422 = validation error — non-retryable
            return PaymentResult.failure("PAYPAL_VALIDATION_ERROR", e.getMessage(), false);
        }
    }

    public PaymentResult paypalFallback(UUID orderId, BigDecimal amount, String currency,
                                         String token, Throwable ex) {
        log.error("PayPal circuit breaker OPEN for order {}: {}", orderId, ex.getMessage());
        return PaymentResult.failure("PAYPAL_CIRCUIT_OPEN",
            "PayPal service unavailable — circuit breaker open", true);
    }

    @CircuitBreaker(name = "stripe", fallbackMethod = "stripeFallback")
    @Retry(name = "stripe")
    public PaymentResult chargeViaStripe(UUID orderId, BigDecimal amount, String currency,
                                          String stripeToken) {

        try {
            // Stripe uses form-encoded requests
            String amountInCents = String.valueOf(amount.multiply(BigDecimal.valueOf(100)).longValue());

            Map<?, ?> response = webClientBuilder.build()
                .post()
                .uri(stripeBaseUrl + "/v1/payment_intents")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue("amount=" + amountInCents
                    + "&currency=" + currency.toLowerCase()
                    + "&payment_method=" + stripeToken
                    + "&confirm=true"
                    + "&metadata[order_id]=" + orderId)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(10))
                .block();

            String transactionId = (String) response.get("id");
            String status = (String) response.get("status");

            if ("succeeded".equals(status)) {
                log.info("Stripe charge successful, txn={}", transactionId);
                return PaymentResult.success(transactionId, "STRIPE");
            } else {
                return PaymentResult.failure("STRIPE_NOT_SUCCEEDED",
                    "Stripe payment status: " + status, false);
            }

        } catch (WebClientResponseException e) {
            log.error("Stripe error for order {}: {} - {}", orderId,
                e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().is4xxClientError()) {
                return PaymentResult.failure("STRIPE_CLIENT_ERROR", e.getMessage(), false);
            }
            throw e; // 5xx → triggers @Retry
        }
    }

    public PaymentResult stripeFallback(UUID orderId, BigDecimal amount, String currency,
                                         String token, Throwable ex) {
        log.error("Stripe circuit breaker OPEN for order {}: {}", orderId, ex.getMessage());
        return PaymentResult.failure("STRIPE_CIRCUIT_OPEN",
            "Stripe service unavailable — circuit breaker open", true);
    }

    public record PaymentResult(
        boolean success,
        String transactionId,
        String provider,
        String errorCode,
        String errorMessage,
        boolean retryable
    ) {
        public static PaymentResult success(String txnId, String provider) {
            return new PaymentResult(true, txnId, provider, null, null, false);
        }
        public static PaymentResult failure(String code, String message, boolean retryable) {
            return new PaymentResult(false, null, null, code, message, retryable);
        }
    }
}
