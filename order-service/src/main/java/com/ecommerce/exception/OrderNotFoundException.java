package com.ecommerce.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(UUID orderId) {
        super("Order not found: " + orderId);
    }
}
