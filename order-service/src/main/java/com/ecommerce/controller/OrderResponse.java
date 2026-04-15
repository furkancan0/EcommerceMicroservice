package com.ecommerce.controller;

import com.ecommerce.entity.Order;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderResponse {

    private UUID id;
    private UUID userId;
    private String status;
    private BigDecimal totalAmount;
    private String currency;
    private List<ItemResponse> items;
    private Instant createdAt;
    private Instant updatedAt;

    public static OrderResponse from(Order order) {
        return OrderResponse.builder()
            .id(order.getId())
            .userId(order.getUserId())
            .status(order.getStatus().name())
            .totalAmount(order.getTotalAmount())
            .currency(order.getCurrency())
            .createdAt(order.getCreatedAt())
            .updatedAt(order.getUpdatedAt())
            .items(order.getItems().stream()
                .map(i -> ItemResponse.builder()
                    .productId(i.getProductId())
                    .productName(i.getProductName())
                    .quantity(i.getQuantity())
                    .unitPrice(i.getUnitPrice())
                    .totalPrice(i.getTotalPrice())
                    .build())
                .collect(Collectors.toList()))
            .build();
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ItemResponse {
        private UUID productId;
        private String productName;
        private int quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;
    }
}
