package com.ecommerce.service;

import com.ecommerce.events.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final JavaMailSender       mailSender;
    private final StringRedisTemplate  redisTemplate;

    private static final Duration DEDUP_TTL   = Duration.ofHours(24);
    private static final String   NOTIF_KEY   = "notif:";
    private static final String   TO_ADDRESS  = "user@example.com"; // TODO: resolve from user-service

    public void sendOrderCreatedNotification(OrderCreatedEvent event) {
        if (!acquireIdempotencyLock(event.getEventId())) return;
        sendEmail(TO_ADDRESS,
            "Order Received — #" + event.getOrderId(),
            """
            Thank you for your order!

            Order ID : %s
            Total    : %s %s
            Status   : Processing

            We will notify you once your order is confirmed.
            """.formatted(event.getOrderId(), event.getCurrency(), event.getTotalAmount()));
    }

    public void sendOrderConfirmedNotification(OrderConfirmedEvent event) {
        if (!acquireIdempotencyLock(event.getEventId())) return;
        sendEmail(TO_ADDRESS,
            "Order Confirmed — #" + event.getOrderId(),
            """
            Great news! Your order has been confirmed and is being prepared.

            Order ID       : %s
            Amount Charged : %s

            You will receive a shipping update soon. Thank you for shopping with us!
            """.formatted(event.getOrderId(), event.getTotalAmount()));
    }

    public void sendOrderCancelledNotification(OrderCancelledEvent event) {
        if (!acquireIdempotencyLock(event.getEventId())) return;
        sendEmail(TO_ADDRESS,
            "Order Cancelled — #" + event.getOrderId(),
            """
            We're sorry, your order has been cancelled.

            Order ID : %s
            Reason   : %s

            If you were charged, a full refund will be processed within 3–5 business days.
            """.formatted(event.getOrderId(), event.getReason()));
    }

    public void sendPaymentSuccessNotification(PaymentCompletedEvent event) {
        if (!acquireIdempotencyLock(event.getEventId())) return;
        sendEmail(TO_ADDRESS,
            "Payment Successful — #" + event.getOrderId(),
            """
            Your payment has been processed successfully.

            Order ID     : %s
            Amount       : %s %s
            Provider     : %s
            Transaction  : %s
            """.formatted(event.getOrderId(), event.getCurrency(), event.getAmount(),
                          event.getProvider(), event.getProviderTransactionId()));
    }

    public void sendPaymentFailedNotification(PaymentFailedEvent event) {
        if (!acquireIdempotencyLock(event.getEventId())) return;
        sendEmail(TO_ADDRESS,
            "Payment Failed — Action Required — #" + event.getOrderId(),
            """
            Unfortunately, your payment could not be processed.

            Order ID : %s
            Reason   : %s

            Please update your payment method and try again, or contact support.
            """.formatted(event.getOrderId(), event.getReason()));
    }

    private boolean acquireIdempotencyLock(String eventId) {
        Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(NOTIF_KEY + eventId, "sent", DEDUP_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            return false;
        }
        return true;
    }

    private void sendEmail(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.debug("Email sent to {}: {}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Email sending failed — will retry via Kafka", e);
        }
    }
}
