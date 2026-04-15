package com.ecommerce.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CreateOrderRequest {

    @NotEmpty(message = "Order must have at least one item")
    @Valid
    private List<OrderItemRequest> items;

    @NotBlank
    @Size(max = 3)
    private String currency = "USD";

    @NotNull @DecimalMin("0.01")
    private BigDecimal totalAmount;

    private String idempotencyKey;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class OrderItemRequest {
        @NotNull
        private UUID productId;

        @NotBlank
        private String productName;

        @Min(1)
        private int quantity;

        @NotNull @DecimalMin("0.01")
        private BigDecimal unitPrice;
    }
}
