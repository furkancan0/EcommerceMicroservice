package com.ecommerce.controller;

import com.ecommerce.entity.Order;
import com.ecommerce.service.CreateOrderRequest;
import com.ecommerce.service.OrderQueryService;
import com.ecommerce.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService      orderService;
    private final OrderQueryService orderQueryService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        request.setIdempotencyKey(idempotencyKey);
        Order order = orderService.createOrder(request, userId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(OrderResponse.from(order));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(
            @PathVariable("orderId") UUID orderId,
            @RequestHeader("X-User-Id") UUID userId) {

        return orderQueryService.findOrder(orderId, userId)
            .map(order -> ResponseEntity.ok(OrderResponse.from(order)))
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<Page<OrderResponse>> listOrders(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<OrderResponse> orders = orderQueryService
            .listOrdersByUser(userId, page, size)
            .map(OrderResponse::from);

        return ResponseEntity.ok(orders);
    }
}
